## Mini-OpenSearch 学习计划（Maven 版）

基于本地 Lucene 11.0.0-SNAPSHOT 源码，从零构建一个简化版搜索引擎，逐步理解 OpenSearch 的核心架构。

---

### 整体思路

OpenSearch 本质上是在 Lucene 之上叠加了四层能力：**HTTP 接入层**、**索引管理层**、**分布式协调层**和**高级功能层**。我们按这个分层自底向上构建，每一阶段都能独立运行和验证。

```
┌─────────────────────────────────────────────┐
│  Phase 7: 高级功能 (聚合/高亮/建议)           │
├─────────────────────────────────────────────┤
│  Phase 6: 多节点集群 (Transport + Discovery) │
├─────────────────────────────────────────────┤
│  Phase 5: 分片与路由 (Shard + Routing)       │
├─────────────────────────────────────────────┤
│  Phase 4: 索引管理 (Mapping + Settings)      │
├─────────────────────────────────────────────┤
│  Phase 3: 文档 CRUD (Index/Get/Update/Del)   │
├─────────────────────────────────────────────┤
│  Phase 2: HTTP 服务 (Netty REST API)         │
├─────────────────────────────────────────────┤
│  Phase 1: Lucene 引擎原型 (单进程索引+搜索)    │
└─────────────────────────────────────────────┘
          ▲
          │ 底层依赖
    ┌─────┴──────┐
    │   Lucene    │  ← 你已有的源码
    │  11.0.0-SNAP│
    └────────────┘
```

---

### 项目初始化

在 `D:\workspace\item\wsl\lucene` 同级创建新项目：

```
D:\workspace\item\wsl\mini-opensearch\
├── pom.xml                         # Maven 构建文件
├── src/main/java/
│   └── org/mini/opensearch/
│       ├── MiniOpenSearch.java          # 启动入口
│       ├── engine/                      # Phase 1: Lucene 引擎
│       ├── http/                        # Phase 2: HTTP 层
│       ├── action/                      # Phase 3: 文档操作
│       ├── index/                       # Phase 4: 索引管理
│       ├── shard/                       # Phase 5: 分片
│       ├── cluster/                     # Phase 6: 集群
│       └── plugin/                      # Phase 7: 高级功能
├── src/main/resources/
│   └── log4j2.xml                       # 日志配置
└── src/test/java/
```

---

### 第一步：将本地 Lucene 安装到 Maven 本地仓库

由于你的 Lucene 是本地源码（Gradle 构建），需要先把它发布到 Maven 本地仓库，mini-opensearch 的 pom.xml 才能引用。

**方式一（推荐）：用 Lucene 自带的 Gradle 任务发布**

```bash
# 在 Lucene 源码目录下执行
cd D:\workspace\item\wsl\lucene
.\gradlew publishToMavenLocal
```

这会把所有 Lucene 模块的 jar 安装到 `~/.m2/repository/org/apache/lucene/` 下，版本号为 `11.0.0-SNAPSHOT`。

**方式二：手动安装单个 jar**

如果方式一有问题，可以手动把核心 jar 安装到本地仓库：

```bash
# 以 lucene-core 为例，其他模块类似
mvn install:install-file ^
  -Dfile=D:\workspace\item\wsl\lucene\lucene\core\build\libs\lucene-core-11.0.0-SNAPSHOT.jar ^
  -DgroupId=org.apache.lucene ^
  -DartifactId=lucene-core ^
  -Dversion=11.0.0-SNAPSHOT ^
  -Dpackaging=jar

# 同理安装 queryparser 和 analysis-common
mvn install:install-file ^
  -Dfile=D:\workspace\item\wsl\lucene\lucene\queryparser\build\libs\lucene-queryparser-11.0.0-SNAPSHOT.jar ^
  -DgroupId=org.apache.lucene ^
  -DartifactId=lucene-queryparser ^
  -Dversion=11.0.0-SNAPSHOT ^
  -Dpackaging=jar

mvn install:install-file ^
  -Dfile=D:\workspace\item\wsl\lucene\lucene\analysis\common\build\libs\lucene-analysis-common-11.0.0-SNAPSHOT.jar ^
  -DgroupId=org.apache.lucene ^
  -DartifactId=lucene-analysis-common ^
  -Dversion=11.0.0-SNAPSHOT ^
  -Dpackaging=jar
```

> 提示：后续 Phase 7 如果需要高亮、suggest 等模块，再按同样方式安装对应的 jar 即可。

---

### pom.xml 完整配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.mini</groupId>
    <artifactId>mini-opensearch</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>Mini-OpenSearch</name>
    <description>A simplified OpenSearch built on top of Lucene for learning</description>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <lucene.version>11.0.0-SNAPSHOT</lucene.version>
        <netty.version>4.1.100.Final</netty.version>
        <jackson.version>2.17.0</jackson.version>
        <log4j2.version>2.23.0</log4j2.version>
        <junit.version>5.10.2</junit.version>
    </properties>

    <dependencies>
        <!-- ========== Lucene 核心（Phase 1 起） ========== -->
        <dependency>
            <groupId>org.apache.lucene</groupId>
            <artifactId>lucene-core</artifactId>
            <version>${lucene.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.lucene</groupId>
            <artifactId>lucene-queryparser</artifactId>
            <version>${lucene.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.lucene</groupId>
            <artifactId>lucene-analysis-common</artifactId>
            <version>${lucene.version}</version>
        </dependency>

        <!-- ========== HTTP 服务（Phase 2 起） ========== -->
        <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-all</artifactId>
            <version>${netty.version}</version>
        </dependency>

        <!-- ========== JSON 处理（Phase 2 起） ========== -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
            <version>${jackson.version}</version>
        </dependency>

        <!-- ========== 日志 ========== -->
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-core</artifactId>
            <version>${log4j2.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-api</artifactId>
            <version>${log4j2.version}</version>
        </dependency>

        <!-- ========== 测试 ========== -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- 编译插件 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>25</source>
                    <target>25</target>
                    <compilerArgs>
                        <arg>--enable-preview</arg>
                    </compilerArgs>
                </configuration>
            </plugin>

            <!-- 测试插件 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <argLine>--enable-preview</argLine>
                </configuration>
            </plugin>

            <!-- 打包可执行 jar（Phase 2 起用） -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.1</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>org.mini.opensearch.MiniOpenSearch</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>

            <!-- assembly：打包含所有依赖的可执行 jar -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>3.7.1</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>org.mini.opensearch.MiniOpenSearch</mainClass>
                        </manifest>
                    </archive>
                    <descriptorRefs>
                        <descriptorRef>jar-with-dependencies</descriptorRef>
                    </descriptorRefs>
                </configuration>
                <executions>
                    <execution>
                        <id>make-assembly</id>
                        <phase>package</phase>
                        <goals>
                            <goal>single</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

### 常用 Maven 命令速查

```bash
# 编译项目
mvn compile

# 运行测试
mvn test

# 打包
mvn package

# 运行主类（需要 exec 插件，或直接用 java -jar）
java -jar target/mini-opensearch-0.1.0-SNAPSHOT-jar-with-dependencies.jar

# 清理
mvn clean

# 跳过测试打包
mvn package -DskipTests
```

---

### Phase 1: Lucene 引擎原型（1-2 天）

**目标**：写一个 Java 类，直接调用 Lucene API 完成索引和搜索，跑通最小闭环。

**核心概念对应**：

| mini-opensearch | Lucene 源码位置 | OpenSearch 对应 |
|---|---|---|
| `MiniEngine` | `lucene/core/.../index/IndexWriter.java` | `InternalEngine` |
| `MiniEngine.search()` | `lucene/core/.../search/IndexSearcher.java` | `SearchService` |
| `MiniDocument` | `lucene/core/.../document/Document.java` | `Document` (ES) |

**要写的代码**：

```java
// 1. MiniEngine.java — 封装 Lucene 的索引+搜索能力
public class MiniEngine implements Closeable {
    private final Directory directory;
    private final IndexWriter writer;
    private final Analyzer analyzer;
    private DirectoryReader reader;
    private IndexSearcher searcher;

    public MiniEngine(Path indexPath) {
        this.directory = MMapDirectory.open(indexPath);
        this.analyzer = new StandardAnalyzer();
        this.writer = new IndexWriter(directory,
            new IndexWriterConfig(analyzer));
        refresh();
    }

    // 索引一个文档
    public void index(String id, Map<String, String> fields) {
        Document doc = new Document();
        doc.add(new StringField("_id", id, Field.Store.YES));
        for (var entry : fields.entrySet()) {
            doc.add(new TextField(entry.getKey(), entry.getValue(), Field.Store.YES));
        }
        writer.updateDocument(new Term("_id", id), doc);
    }

    // 搜索
    public List<Map<String, Object>> search(String queryString, int topN) {
        refresh();
        QueryParser parser = new QueryParser("content", analyzer);
        Query query = parser.parse(queryString);
        TopDocs topDocs = searcher.search(query, topN);
        // ... 将 ScoreDoc 转为 Map 返回
    }

    // 刷新 searcher（近实时）
    private void refresh() {
        DirectoryReader newReader = DirectoryReader.open(directory);
        if (reader == null) {
            reader = newReader;
        } else {
            reader = DirectoryReader.openIfChanged(newReader, reader);
            if (reader == null) reader = newReader;
        }
        searcher = new IndexSearcher(reader);
    }
}
```

**验证方式**：写 `main()` 方法，索引 5 篇文档，搜索关键词，打印结果。

```bash
# 编译并运行
mvn compile
java --enable-preview -cp target/classes org.mini.opensearch.engine.MiniEngine
```

**源码阅读指引**：

- `IndexWriter.addDocument()` → 理解文档如何经过 Analysis 链变成倒排索引
- `IndexSearcher.search()` → 理解 Query → Weight → Scorer → Collector 流程
- `StandardAnalyzer` → 理解分词器的工作原理
- `MMapDirectory` → 理解 Lucene 如何做文件 I/O

---

### Phase 2: HTTP 服务层（2-3 天）

**目标**：用 Netty 启动 HTTP 服务，暴露 REST API，让外部可以通过 curl 调用。

**核心概念对应**：

| mini-opensearch | OpenSearch 对应 |
|---|---|
| `HttpServer` | `Netty4HttpServerTransport` |
| `RestHandler` | `RestController` + 各种 `RestXxxAction` |
| `RestIndexAction` | `RestIndexAction` |
| `RestSearchAction` | `RestSearchAction` |

**要写的代码**：

```java
// 2. HttpServer.java — Netty HTTP 服务
public class HttpServer {
    private final int port;
    private final Map<String, RestHandler> handlers = new HashMap<>();

    public void registerHandler(String method, String path, RestHandler handler) {
        handlers.put(method + " " + path, handler);
    }

    public void start() {
        // Netty ServerBootstrap 配置
        // 路由到对应的 RestHandler
    }
}

// 3. RestHandler.java — 请求处理接口
public interface RestHandler {
    void handle(HttpRequest request, HttpResponse response) throws Exception;
}

// 4. RestSearchAction.java — 搜索 REST 接口
public class RestSearchAction implements RestHandler {
    private final MiniEngine engine;

    @Override
    public void handle(HttpRequest request, HttpResponse response) {
        // 解析 JSON body: { "query": "...", "size": 10 }
        // 调用 engine.search()
        // 返回 JSON: { "hits": { "total": N, "hits": [...] } }
    }
}
```

**API 设计**（对齐 OpenSearch 风格）：

```bash
# 索引文档
PUT /my_index/_doc/1
{ "title": "Hello World", "content": "OpenSearch is great" }

# 搜索
GET /my_index/_search
{ "query": "OpenSearch", "size": 10 }

# 删除文档
DELETE /my_index/_doc/1
```

**验证方式**：启动服务后，用 curl 发请求，得到 JSON 响应。

```bash
# 打包并启动
mvn package -DskipTests
java --enable-preview -jar target/mini-opensearch-0.1.0-SNAPSHOT-jar-with-dependencies.jar

# 另一个终端测试
curl -X PUT http://localhost:9200/my_index/_doc/1 -d "{\"title\":\"Hello\",\"content\":\"test\"}"
curl http://localhost:9200/my_index/_search?query=test
```

**源码阅读指引**：

- 对比 OpenSearch 的 `Netty4HttpServerTransport` 理解 HTTP 层架构
- 对比 `RestController` 理解路由注册机制
- 对比 `RestSearchAction` 理解请求解析流程

---

### Phase 3: 文档 CRUD 操作（2-3 天）

**目标**：实现完整的文档增删改查，理解 Lucene 的文档生命周期。

**核心概念对应**：

| mini-opensearch | Lucene 源码位置 | OpenSearch 对应 |
|---|---|---|
| `IndexService.index()` | `IndexWriter.updateDocument()` | `TransportIndexAction` |
| `IndexService.delete()` | `IndexWriter.deleteDocuments()` | `TransportDeleteAction` |
| `IndexService.get()` | `IndexSearcher` + `TermQuery` | `TransportGetAction` |
| `IndexService.update()` | `IndexWriter.updateDocument()` | `TransportUpdateAction` |

**关键实现要点**：

1. **Index（写入）**：将 JSON 文档转为 Lucene `Document`，字段类型映射（text → TextField，keyword → StringField，long → LongField）
2. **Get（读取）**：用 `_id` 的 TermQuery 搜索，返回存储的字段
3. **Delete（删除）**：调用 `IndexWriter.deleteDocuments(new Term("_id", id))`
4. **Update（更新）**：本质是 delete + index（Lucene 的 updateDocument 就是这个语义）
5. **Bulk（批量）**：循环调用上述操作，理解 OpenSearch bulk API 的 NDJSON 格式

**源码阅读指引**：

- `IndexWriter.updateDocument()` 内部如何做到原子性的"先删后加"
- `IndexWriter.deleteDocuments()` 的缓冲删除机制（`BufferedUpdates`）
- `SegmentReader.document()` 如何从存储字段还原文档

---

### Phase 4: 索引管理（2-3 天）

**目标**：支持创建/删除索引，配置 Mapping 和 Settings。

**核心概念对应**：

| mini-opensearch | Lucene 源码位置 | OpenSearch 对应 |
|---|---|---|
| `IndexRegistry` | (无直接对应) | `IndicesService` / `IndexNameExpressionResolver` |
| `Mapping` | `FieldInfo` / `FieldType` | `MappingMetadata` / `MappedFieldType` |
| `Settings` | `IndexWriterConfig` | `IndexScopedSettings` |

**要写的代码**：

```java
// IndexRegistry.java — 管理多个索引（每个索引一个 MiniEngine）
public class IndexRegistry {
    private final Map<String, MiniEngine> indices = new ConcurrentHashMap<>();

    public void createIndex(String name, IndexSettings settings) {
        Path path = Path.of("data", name);
        MiniEngine engine = new MiniEngine(path, settings);
        indices.put(name, engine);
    }

    public void deleteIndex(String name) {
        MiniEngine engine = indices.remove(name);
        engine.close();
        // 删除目录
    }

    public MiniEngine getIndex(String name) {
        return indices.get(name);
    }
}
```

**Mapping 设计**（简化版）：

```json
PUT /my_index
{
  "settings": {
    "number_of_shards": 1,
    "analysis": { "analyzer": "standard" }
  },
  "mappings": {
    "properties": {
      "title":   { "type": "text" },
      "status":  { "type": "keyword" },
      "count":   { "type": "long" },
      "created": { "type": "date" }
    }
  }
}
```

Mapping 的作用是将 JSON 字段类型映射为 Lucene 的 Field 类型：

| Mapping type | Lucene Field | 说明 |
|---|---|---|
| `text` | `TextField` | 分词，可全文搜索 |
| `keyword` | `StringField` | 不分词，精确匹配 |
| `long` / `integer` | `LongField` / `IntField` | 数值范围查询 |
| `date` | `LongField` | 日期转为 epoch millis |

**源码阅读指引**：

- `IndexWriterConfig` 的每个参数对应 OpenSearch 的哪个 setting
- `FieldInfo` 如何记录每个字段的索引选项（DOCS / DOCS_AND_FREQS / DOCS_AND_FREQS_AND_POSITIONS）
- `TieredMergePolicy` 的参数如何影响段合并

---

### Phase 5: 分片与路由（3-4 天）

**目标**：一个索引可以有多个分片（每个分片是一个独立的 Lucene 索引），文档通过路由哈希分配到分片。

**核心概念对应**：

| mini-opensearch | Lucene 源码位置 | OpenSearch 对应 |
|---|---|---|
| `Shard` | `MiniEngine`（一个 Lucene 索引） | `IndexShard` |
| `ShardRouter` | (无直接对应) | `RoutingService` |
| `ShardManager` | (无直接对应) | `IndicesClusterStateService` |

**关键实现**：

```java
// ShardRouter.java — 路由计算
public class ShardRouter {
    private final int numShards;

    public int route(String id) {
        // 与 OpenSearch 一致：hash(routing) % num_shards
        return Math.floorMod(hash(id), numShards);
    }

    private int hash(String value) {
        // 简化版：使用 MurmurHash3
        return MurmurHash3.hash32(value.getBytes(StandardCharsets.UTF_8));
    }
}

// 写入时路由
public void index(String indexName, String id, Map<String, Object> source) {
    int shardId = router.route(id);
    Shard shard = shardManager.getShard(indexName, shardId);
    shard.indexDocument(id, source);
}

// 搜索时扇出（scatter）
public SearchResult search(String indexName, String query) {
    List<Shard> shards = shardManager.getShards(indexName);
    // 并行搜索所有分片
    List<Future<ShardResult>> futures = shards.stream()
        .map(shard -> executor.submit(() -> shard.search(query, topN)))
        .toList();
    // 合并结果（gather），按分数排序取 topN
    return mergeResults(futures);
}
```

**源码阅读指引**：

- OpenSearch 的 `OperationRouting.indexShards()` 理解路由策略
- `IndexSearcher` 的 `search()` 在分片场景下的 scatter-gather 模式
- Lucene `MultiReader` 如何合并多个 reader（类比分片结果合并）

---

### Phase 6: 多节点集群（4-5 天）

**目标**：多个进程组成集群，分片分布在不同节点上，实现基本的节点发现和状态同步。

**核心概念对应**：

| mini-opensearch | OpenSearch 对应 |
|---|---|
| `Node` | `Node` / `OpenSearchNode` |
| `ClusterState` | `ClusterState` |
| `TransportService` | `TransportService` (Netty4Transport) |
| `Discovery` | `Coordinator` / `ZenDiscovery` |
| `ShardAllocation` | `AllocationService` / `ShardsAllocator` |

**架构设计**：

```
Node A (master + data)          Node B (data)
┌────────────────────┐          ┌────────────────────┐
│ HTTP Server :9200  │          │ HTTP Server :9201  │
├────────────────────┤          ├────────────────────┤
│ Shard 0 (index_1)  │          │ Shard 1 (index_1)  │
│ Shard 0 (index_2)  │          │ Shard 1 (index_2)  │
├────────────────────┤          ├────────────────────┤
│ Transport :9300    │◄────────►│ Transport :9301    │
├────────────────────┤          ├────────────────────┤
│ ClusterState       │          │ ClusterState       │
│ Discovery          │          │ Discovery          │
└────────────────────┘          └────────────────────┘
```

**Transport 层**（节点间通信）：

```java
// TransportService.java — 节点间 RPC
public class TransportService {
    private final int transportPort;
    private final Map<String, TransportAction> actions = new HashMap<>();

    // 注册处理动作
    public void registerAction(String actionName, TransportAction action) {
        actions.put(actionName, action);
    }

    // 发送请求到另一个节点
    public void sendRequest(String nodeId, String actionName,
                            byte[] request, Consumer<byte[]> callback) {
        // 通过 Netty TCP 客户端发送
    }
}
```

**简化版集群发现**（Seed-based，不用 Zen/Paxos）：

```java
// SimpleDiscovery.java
public class SimpleDiscovery {
    private final List<String> seedNodes;
    private final Set<String> knownNodes = new HashSet<>();

    // 启动时连接 seed 节点，获取集群中的其他节点
    public void discover() {
        for (String seed : seedNodes) {
            // 连接 seed，请求节点列表
            // 将新节点加入 knownNodes
        }
    }
}
```

**ClusterState 同步**：

```java
// ClusterState.java — 集群元数据
public class ClusterState {
    private final Map<String, IndexMetadata> indices;
    private final Map<String, NodeInfo> nodes;
    private final Map<ShardId, String> shardAllocation; // shard → nodeId
    private final String masterNodeId;
    private final long version;
}
```

**源码阅读指引**：

- OpenSearch `TransportService` 理解节点间通信协议
- `ClusterState` 理解集群元数据结构
- `AllocationService.reroute()` 理解分片分配算法
- `Coordinator` 理解主节点选举（可简化为固定 master）

---

### Phase 7: 高级功能（持续迭代）

按需实现，每个功能独立模块。实现前需先用 `mvn install:install-file` 安装对应的 Lucene 模块 jar：

| 功能 | 涉及 Lucene 模块 | OpenSearch 对应 | 难度 |
|---|---|---|---|
| **聚合（Aggregations）** | `facet` 模块 | `AggregationService` | 中 |
| **高亮（Highlighting）** | `highlighter` 模块 | `HighlightService` | 低 |
| **自动补全（Suggest）** | `suggest` 模块 | `SearchService` | 低 |
| **向量搜索（KNN）** | `core` 的 KnnFloatVectorField | `KnnService` | 中 |
| **分析器插件** | `analysis/*` 各模块 | `AnalysisModule` | 低 |
| **Translog** | (Lucene 无，需自己实现) | `TranslogService` | 高 |
| **Segment Replication** | `replicator` 模块 | `ReplicationService` | 高 |

如果用到额外 Lucene 模块，需要在 pom.xml 中添加对应依赖，例如高亮：

```xml
<!-- Phase 7: 高亮功能 -->
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-highlighter</artifactId>
    <version>${lucene.version}</version>
</dependency>

<!-- Phase 7: 自动补全 -->
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-suggest</artifactId>
    <version>${lucene.version}</version>
</dependency>
```

---

### 推荐的源码阅读顺序

按以下顺序阅读 Lucene 源码，每个文件标注了"为什么要读"：

**第一周：理解数据流**

1. `lucene/demo/src/java/.../IndexFiles.java` — 最简单的索引示例，5 分钟读完
2. `lucene/demo/src/java/.../SearchFiles.java` — 最简单的搜索示例
3. `lucene/core/.../document/Document.java` — 文档模型
4. `lucene/core/.../document/Field.java` + `TextField.java` + `StringField.java` — 字段类型
5. `lucene/core/.../analysis/Analyzer.java` — 分析器抽象
6. `lucene/core/.../analysis/standard/StandardAnalyzer.java` — 默认分析器

**第二周：理解索引内部**

7. `lucene/core/.../index/IndexWriter.java` — 核心写入器（重点看 `addDocument`、`updateDocument`、`deleteDocuments`、`commit`、`forceMerge`）
8. `lucene/core/.../index/IndexWriterConfig.java` — 理解所有可配参数
9. `lucene/core/.../index/DirectoryReader.java` — 读取器入口
10. `lucene/core/.../index/MergePolicy.java` + `TieredMergePolicy.java` — 段合并策略
11. `lucene/core/.../store/Directory.java` — 存储抽象
12. `lucene/core/.../store/MMapDirectory.java` — 内存映射文件

**第三周：理解搜索内部**

13. `lucene/core/.../search/IndexSearcher.java` — 搜索核心（重点看 `search` 方法）
14. `lucene/core/.../search/Query.java` — 查询基类
15. `lucene/core/.../search/TermQuery.java` — 最简单的查询实现
16. `lucene/core/.../search/BooleanQuery.java` — 组合查询
17. `lucene/core/.../search/TopDocs.java` + `ScoreDoc.java` — 结果模型
18. `lucene/core/.../search/Collector.java` — 结果收集器
19. `lucene/core/.../search/similarities/BM25Similarity.java` — 评分算法
20. `lucene/queryparser/.../classic/QueryParser.java` — 查询解析

**第四周：理解高级特性**

21. `lucene/core/.../search/SearcherManager.java` — NRT searcher 生命周期
22. `lucene/core/.../index/BufferedUpdates.java` — 缓冲删除
23. `lucene/core/.../codecs/Codec.java` — 编解码器 SPI
24. `lucene/core/.../util/fst/` — FST 数据结构（词项字典）
25. `lucene/core/.../util/bkd/` — BKD 树（多维点索引）

---

### 技术栈总结

| 组件 | 选型 | 理由 |
|---|---|---|
| 构建工具 | Maven | 标准化依赖管理，生态成熟 |
| HTTP 服务 | Netty 4.x | OpenSearch 也用 Netty |
| JSON 处理 | Jackson | OpenSearch 也用 Jackson |
| 日志 | Log4j2 | 与 Lucene/OpenSearch 一致 |
| 测试 | JUnit 5 | 与 Lucene 一致 |
| Java 版本 | 25+ | Lucene 11.0.0 要求 |

---

### 里程碑检查清单

| 阶段 | 完成标志 | 预计时间 |
|---|---|---|
| Phase 1 | 能在 main() 中索引和搜索文档 | 1-2 天 |
| Phase 2 | curl 能调通 PUT/GET/DELETE/POST | 2-3 天 |
| Phase 3 | 文档 CRUD 完整可用，bulk 接口可用 | 2-3 天 |
| Phase 4 | 能创建索引、配置 mapping、删除索引 | 2-3 天 |
| Phase 5 | 多分片索引，路由和 scatter-gather 搜索 | 3-4 天 |
| Phase 6 | 两个进程组成集群，跨节点搜索 | 4-5 天 |
| Phase 7 | 至少实现聚合或高亮之一 | 持续迭代 |

总计约 **3-4 周**可完成 Phase 1-6 的核心骨架。
