# Polybot

**开源 Polymarket 交易基础设施和策略反向工程工具包。**

Polybot 为 [Polymarket](https://polymarket.com) 预测市场提供完整的交易基础设施，以及强大的工具来分析和反向工程任何用户的成功交易策略。

![策略分析仪表板](docs/showcase_readme.png)

## 功能特性

### 交易基础设施
- **执行服务（Executor Service）**：低延迟订单执行，支持模拟交易
- **策略服务（Strategy Service）**：可插拔的策略框架，支持自动化交易
- **实时市场数据**：WebSocket 集成，提供订单簿和交易流
- **持仓管理**：自动跟踪、结算和代币赎回
- **风险管理**：可配置的限制、紧急停止和风险敞口上限

### 策略研究与反向工程
- **用户交易分析**：采集和分析任何 Polymarket 用户的交易历史
- **模式识别**：识别入场/出场信号、仓位规则和时机模式
- **复制评分**：将你的机器人决策与目标策略进行比较
- **回测框架**：基于历史数据测试策略

### 分析管道
- **ClickHouse 集成**：高性能时序分析
- **事件流**：基于 Kafka 的事件管道，支持实时分析
- **监控**：Grafana 仪表板和 Prometheus 指标

## 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Polybot 架构                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   策略服务   │  │   执行服务   │  │      数据采集服务     │  │
│  │              │──│              │  │                      │  │
│  │ • 策略       │  │ • 订单管理   │  │ • 用户交易            │  │
│  │ • 信号       │  │ • 模拟器     │  │ • 市场数据            │  │
│  │ • 持仓       │  │ • 结算       │  │ • 链上事件            │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│         │                 │                    │                 │
│         └─────────────────┼────────────────────┘                 │
│                           │                                      │
│                    ┌──────▼──────┐                               │
│                    │   Kafka     │                               │
│                    │   事件流    │                               │
│                    └──────┬──────┘                               │
│                           │                                      │
│                    ┌──────▼──────┐                               │
│                    │ ClickHouse  │                               │
│                    │   分析      │                               │
│                    └─────────────┘                               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## 快速开始

### 前置要求
- Java 21+
- Maven 3.8+
- Docker & Docker Compose
- Python 3.11+（用于研究工具）

### 1. 克隆和配置

```bash
git clone https://github.com/yourusername/polybot.git
cd polybot

# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，填入你的配置
# 至少需要设置 POLYMARKET_TARGET_USER 用于分析
```

### 2. 启动基础设施

```bash
# 启动 ClickHouse 和 Kafka（RedPanda）
docker-compose -f docker-compose.analytics.yaml up -d

# 可选：启动监控栈
docker-compose -f docker-compose.monitoring.yaml up -d

# 或者使用基础设施编排服务自动管理
# 启动 infrastructure-orchestrator-service 会自动启动所有基础设施栈
```

### 3. 构建和运行服务

```bash
# 构建所有服务
mvn clean package -DskipTests

# 启动执行服务（默认模拟交易模式）
cd executor-service && mvn spring-boot:run

# 启动策略服务（在另一个终端）
cd strategy-service && mvn spring-boot:run

# 启动数据采集服务（在另一个终端）- 采集目标用户的交易
cd ingestor-service && mvn spring-boot:run

# 启动分析服务（在另一个终端）
cd analytics-service && mvn spring-boot:run

# 启动基础设施编排服务（在另一个终端）- 自动管理 Docker Compose 栈
cd infrastructure-orchestrator-service && mvn spring-boot:run
```

### 4. 研究与分析

```bash
cd research

# 创建 Python 虚拟环境
python3 -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt

# 对目标用户数据进行快照
python snapshot_report.py

# 运行深度分析
python deep_analysis.py

# 比较你的机器人执行结果与目标策略
python sim_trade_match_report.py
```

## 配置

### 环境变量

| 变量名 | 描述 | 必需 |
|--------|------|------|
| `POLYMARKET_TARGET_USER` | 要分析/复制的用户名 | 是（用于研究） |
| `POLYMARKET_PRIVATE_KEY` | 钱包私钥 | 用于实盘交易 |
| `POLYMARKET_API_KEY` | API 凭证 | 用于实盘交易 |
| `ANALYTICS_DB_URL` | ClickHouse 连接 | 用于分析 |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 服务器地址 | 用于事件流 |
| `POLYGON_RPC_URL` | Polygon RPC 节点 | 用于链上数据 |

详细配置说明请参考 [.env.example](.env.example) 文件。

### 交易模式

| 模式 | 描述 |
|------|------|
| `PAPER` | 模拟交易（默认） |
| `LIVE` | 实盘交易 |

在 `application-develop.yaml` 中通过 `hft.mode` 配置项设置。

## 服务说明

### 执行服务（Executor Service）- 端口 8080
处理订单执行、持仓管理和结算。

**主要功能：**
- 订单管理（下单、取消、查询）
- 模拟交易引擎
- 链上交易执行
- 自动结算和代币赎回

**API 示例：**
```bash
# 健康检查
curl http://localhost:8080/api/polymarket/health

# 查询持仓
curl http://localhost:8080/api/polymarket/positions

# 查看结算计划
curl http://localhost:8080/api/polymarket/settlement/plan
```

### 策略服务（Strategy Service）- 端口 8081
运行交易策略并生成交易信号。

**主要功能：**
- 策略执行引擎
- 市场数据分析
- 交易信号生成
- 与执行服务通信

**API 示例：**
```bash
# 查询策略状态
curl http://localhost:8081/api/strategy/status
```

### 数据采集服务（Ingestor Service）- 端口 8083
采集市场数据和用户交易数据，写入 ClickHouse。

**主要功能：**
- 用户交易历史采集
- 市场数据采集（订单簿、交易流）
- Polygon 链上事件采集
- 数据写入 ClickHouse

### 分析服务（Analytics Service）- 端口 8082
提供基于 ClickHouse 数据的分析 API。

**主要功能：**
- 事件查询 API
- 用户持仓分析
- 交易统计分析

**API 示例：**
```bash
# 查询最近事件
curl http://localhost:8082/api/analytics/events?limit=100

# 查询特定类型事件
curl http://localhost:8082/api/analytics/events?type=order_placed&limit=50
```

### 基础设施编排服务（Infrastructure Orchestrator Service）- 端口 8084
自动管理 Docker Compose 基础设施栈的启动和停止。

**主要功能：**
- 自动启动/停止 Docker Compose 栈
- 健康检查
- 启动顺序管理

**API 示例：**
```bash
# 查询基础设施状态
curl http://localhost:8084/api/infrastructure/status
```

## 内置策略：完整套利策略

仓库包含一个完全实现的 **完整套利策略**，适用于 Polymarket Up/Down 二元市场：

- **Edge 检测**：识别 UP + DOWN 价格总和小于 $1 的情况
- **库存倾斜**：调整报价以平衡持仓
- **快速补仓**：在部分成交后快速完成配对
- **Taker 模式**：当 edge 有利时跨价差吃单

详细文档请参考 [docs/EXAMPLE_STRATEGY_SPEC.md](docs/EXAMPLE_STRATEGY_SPEC.md)。

## 研究工具

`research/` 目录包含用于策略分析的 Python 工具：

| 脚本 | 用途 |
|------|------|
| `snapshot_report.py` | 创建数据快照用于分析 |
| `deep_analysis.py` | 全面的策略分析 |
| `replication_score.py` | 评分你的复制效果 |
| `sim_trade_match_report.py` | 比较模拟交易与目标执行 |
| `paper_trading_dashboard.py` | Jupyter 仪表板用于监控 |

## 项目结构

```
polybot/
├── executor-service/              # 订单执行和结算
├── strategy-service/              # 交易策略
├── ingestor-service/              # 数据采集
├── analytics-service/             # 分析 API
├── infrastructure-orchestrator-service/  # 基础设施编排
├── polybot-core/                 # 共享库
├── research/                     # Python 分析工具
├── docs/                         # 文档
└── monitoring/                   # Grafana/Prometheus 配置
```

## 配置文件说明

### application.yaml
每个服务的基础配置文件，包含：
- 应用名称
- 服务器端口
- 环境切换配置（导入公共配置）
- 激活的 profile

### application-develop.yaml
开发环境特定配置，包含：
- 数据源配置
- 业务逻辑配置
- 日志级别
- 功能开关

### polybot-common.yaml
所有服务共享的公共配置，包含：
- Kafka 配置
- 监控配置
- 日志配置

详细配置说明请参考各服务的配置文件中的中文注释。

## 贡献

我们欢迎贡献！请参考 [CONTRIBUTING.md](CONTRIBUTING.md) 了解指南。

### 贡献想法
- 新的交易策略
- 支持更多市场类型
- 改进的分析和可视化
- 更好的回测框架
- 更多反向工程工具

## 免责声明

**本软件仅供教育和研究用途。**

- 交易预测市场涉及重大财务风险
- 过往表现不能保证未来结果
- 您对自己的交易决策负全部责任
- 在使用真实资金之前，请始终从模拟交易开始

## 许可证

MIT 许可证 - 详情请参阅 [LICENSE](LICENSE) 文件。

---

**基于对 Polymarket 上成功交易者如何运作的好奇心而构建。**

