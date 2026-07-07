package com.inshi.opensearch.test;

import com.inshi.opensearch.engine.MiniEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class os_test {

    public static void main(String[] args) throws Exception {

        Path tempDir = Path.of("data", "test-index");

        try (MiniEngine miniEngine = new MiniEngine(tempDir)) {
            System.out.println("===== Phase 1: Lucene 引擎原型验证 =====\n");

            // ① 索引 5 篇文档
            System.out.println("--- 写入文档 ---");
            miniEngine.index("1", Map.of(
                    "title", "Introduction to Lucene",
                    "content", "Lucene is a high-performance search engine library written in Java"
            ));
            miniEngine.index("2", Map.of(
                    "title", "OpenSearch Architecture",
                    "content", "OpenSearch is built on top of Lucene and adds distributed search capabilities"
            ));
            miniEngine.index("3", Map.of(
                    "title", "Understanding Inverted Index",
                    "content", "An inverted index maps terms to the documents that contain them"
            ));
            miniEngine.index("4", Map.of(
                    "title", "Full Text Search Basics",
                    "content", "Full text search analyzes and indexes every word in the document content"
            ));
            miniEngine.index("5", Map.of(
                    "title", "BM25 Scoring Algorithm",
                    "content", "BM25 is the default scoring algorithm used in Lucene and OpenSearch"
            ));

            miniEngine.commitAndRefresh();
            System.out.println("文档总数: " + miniEngine.docCount());

            // ② 搜索 "Lucene"
            System.out.println("\n--- 搜索: 'Lucene' ---");
            var results = miniEngine.search("Lucene", 10);
            for (var hit : results) {
                System.out.printf("  [_id=%s, score=%.4f] title=%s%n",
                        hit.get("_id"), hit.get("_score"), hit.get("title"));
            }
            System.out.println("  命中数: " + results.size());

            // ③ 搜索 "search AND distributed"
            System.out.println("\n--- 搜索: 'search AND distributed' ---");
            results = miniEngine.search("search AND distributed", 10);
            for (var hit : results) {
                System.out.printf("  [_id=%s, score=%.4f] title=%s%n",
                        hit.get("_id"), hit.get("_score"), hit.get("title"));
            }

            // ④ 搜索指定字段 "title:BM25"
            System.out.println("\n--- 搜索: 'title:BM25' ---");
            results = miniEngine.search("title:BM25", 10);
            for (var hit : results) {
                System.out.printf("  [_id=%s, score=%.4f] title=%s%n",
                        hit.get("_id"), hit.get("_score"), hit.get("title"));
            }

            // ⑤ 删除文档后再搜索
            System.out.println("\n--- 删除 _id=1，再搜索 'Lucene' ---");
            miniEngine.delete("1");
            miniEngine.commitAndRefresh();
            System.out.println("文档总数: " + miniEngine.docCount());
            results = miniEngine.search("Lucene", 10);
            for (var hit : results) {
                System.out.printf("  [_id=%s, score=%.4f] title=%s%n",
                        hit.get("_id"), hit.get("_score"), hit.get("title"));
            }

            System.out.println("\n===== 验证完成 =====");

        }
    }
}
