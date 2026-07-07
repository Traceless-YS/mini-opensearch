package com.inshi.opensearch.engine;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * 1. MiniEngine.java — 封装 Lucene 的索引+搜索能力
 */
public class MiniEngine implements Closeable {


    // Lucene 的存储抽象层，代表索引的底层存储位置。
    private Directory directory ;

    // Lucene 的索引写入器，负责增、删、改文档
    private IndexWriter writer;

    // Lucene 的分析器，负责将文本转换为词元
    private Analyzer analyzer;

    // Lucene 的索引读取器，负责读取索引
    private DirectoryReader reader;

    // Lucene 的搜索器，负责执行搜索
    private IndexSearcher searcher;




    public MiniEngine(Path indexPath) throws IOException {
        this.directory = MMapDirectory.open(indexPath);
        this.analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.writer = new IndexWriter(directory, config);

        // 提交一次空 commit， 确保 DirectoryReader 能打开
        writer.commit();

        refresh();
    }


    /**
     * 索引一个文档（写入或更新）
     *
     * @param id     文档唯一 ID，对应 OpenSearch 的 _id
     * @param fields 字段名 → 字段值的映射
     */
    public void index(String id, Map<String, String> fields) throws IOException {

        Document doc = new Document();

        // _id  用 StringField （不分词，精确匹配），且存储
        doc.add(new StringField("_id", id, Field.Store.YES));

        for (var entry : fields.entrySet()){
            // 普通文本字段用 TextField (会分词)，且存储
            doc.add(new TextField(entry.getKey(), entry.getValue(), Field.Store.YES));
        }

        // updateDocument = 先删除 _id 相同的旧文档， 再添加新文档 (原子操作)
        writer.updateDocument(new Term("_id", id), doc);

    }


    /**
     * 根据 _id 删除文档
     */
    public void delete(String id) throws IOException {
        writer.deleteDocuments(new Term("_id", id));
    }

    /**
     * 搜索文档
     *
     * @param queryString Lucene 查询语法字符串（如 "title:hello AND content:world"）
     * @param topN       返回前 N 条结果
     * @return 搜索结果列表，每条包含 _id、score 和存储字段
     */
    public List<Map<String, Object>> search(String queryString, int topN) throws Exception {
        refresh();
        if (searcher == null) {
            return List.of();
        }

        // 默认搜索字段为 content
        QueryParser parser = new QueryParser("content", analyzer);
        var query = parser.parse(queryString);
        TopDocs topDocs = searcher.search(query, topN);

        ArrayList<Map<String, Object>> results = new ArrayList<>();
        var storedFields = searcher.storedFields();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
//            Document doc = searcher.storableFields(scoreDoc.doc).storedFields();
            Document doc  = storedFields.document(scoreDoc.doc);
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("_id", doc.get("_id"));
            hit.put("_score", scoreDoc.score);
            // 把所有存储字段都放进去
            for (var field: doc.getFields()){
                if (!"_id".equals(field.name())){
                    hit.put(field.name(), field.stringValue());
                }
            }
            results.add(hit);
        }

        return results;

    }

    /**
     * 获取当前索引中的文档总数
     */
    public int docCount() throws IOException {
        refresh();
        return reader != null ? reader.numDocs() : 0;
    }

    /**
     * 强制提交并刷新（确保写入的文档立即可搜索）
     *
     * Lucene 10.x 注意：IndexWriter 变更缓存在内存，
     * 必须 commit() 后 DirectoryReader.openIfChanged() 才能看到新文档
     */
    public void commitAndRefresh() throws IOException {
        writer.commit();
        refresh();
    }

    /**
     * 刷新 searcher（近实时 NRT）
     *
     * 流程：writer.commit() → 打开新 reader → 用 openIfChanged 增量更新
     */
    private void refresh() throws IOException {
        // 先 commit，确保磁盘上有最新数据
        writer.commit();

        DirectoryReader newReader = DirectoryReader.openIfChanged(reader != null ? reader : DirectoryReader.open(directory));

        if (newReader != null) {
            if (reader != null) reader.close();
            reader = newReader;
        } else if (reader == null) {
            reader = DirectoryReader.open(directory);
        }

        // newReader == null 表示没有变化， 复用旧 reader
        searcher = new IndexSearcher(reader);

    }


    @Override
    public void close() throws IOException {
        writer.close();
        if (reader != null) reader.close();
        directory.close();
    }

}
