# Polybot 使用指南

## 一、访问 Grafana 查看监控仪表板

### 步骤 1: 打开 Grafana
1. 在浏览器中访问: **http://localhost:3000**
2. 登录信息:
   - 用户名: `admin`
   - 密码: `changeme` (首次登录会要求修改密码)

### 步骤 2: 查看预配置的仪表板
1. 登录后，点击左侧菜单的 **"Dashboards"** (仪表板)
2. 找到 **"Polybot - Trading Overview"** 仪表板
3. 点击打开，你将看到:
   - 📊 已实现盈亏 (Realized PnL)
   - 📈 未实现盈亏 (Unrealized PnL)
   - 💰 总持仓 (Total Exposure)
   - 📉 订单统计 (订单数量、成交率等)
   - ⚡ 系统指标 (CPU、内存使用率)

### 步骤 3: 探索仪表板功能
- **时间范围选择**: 右上角可以选择时间范围 (最近1小时、6小时、24小时等)
- **自动刷新**: 仪表板每10秒自动刷新
- **面板交互**: 点击面板可以查看详细数据

### 步骤 4: 创建自定义仪表板 (可选)
1. 点击 **"+"** → **"Create Dashboard"**
2. 添加面板，使用 Prometheus 查询 (见下方 Prometheus 部分)

---

## 二、使用 Prometheus 查询指标

### 步骤 1: 访问 Prometheus UI
1. 在浏览器中访问: **http://localhost:9090**
2. 你将看到 Prometheus 查询界面

### 步骤 2: 查看可用的指标
1. 点击顶部菜单 **"Status"** → **"Targets"**
2. 查看所有被监控的服务:
   - `executor-service` (端口 8080)
   - `strategy-service` (端口 8081)
   - `analytics-service` (端口 8082)
   - `ingestor-service` (端口 8083)
   - `node-exporter` (系统指标)

### 步骤 3: 常用查询示例

#### 3.1 查看所有可用指标
在查询框中输入:
```
{__name__=~".+"}
```
然后点击 **"Execute"**，在 **"Graph"** 标签页查看

#### 3.2 策略相关指标
```promql
# 每日已实现盈亏
polybot_strategy_daily_realized_pnl_usd

# 未实现盈亏
polybot_strategy_unrealized_pnl_usd

# 累计盈亏
polybot_strategy_cumulative_pnl_usd

# 总持仓金额
polybot_strategy_total_exposure_usd

# 当前资金
polybot_strategy_bankroll_usd

# 库存不平衡
polybot_strategy_inventory_imbalance

# 完整套利机会 (Gabagool策略)
polybot_gabagool_complete_set_edge
```

#### 3.3 订单相关指标
```promql
# 订单总数
polybot_orders_placed_total

# 已成交订单数
polybot_orders_filled_total

# 已取消订单数
polybot_orders_cancelled_total

# 被拒绝订单数
polybot_orders_rejected_total

# 按状态分类的订单
polybot_orders_total{status="filled"}
polybot_orders_total{status="cancelled"}
polybot_orders_total{status="rejected"}

# 平均滑点
polybot_order_slippage_ticks
```

#### 3.4 服务健康指标
```promql
# 服务启动时间
application_started_time_seconds

# JVM 内存使用
jvm_memory_used_bytes{application="executor-service"}

# HTTP 请求数
http_server_requests_seconds_count

# 系统 CPU 使用率
100 - (avg(irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
```

#### 3.5 时间序列查询
```promql
# 查看最近1小时的盈亏趋势
rate(polybot_strategy_daily_realized_pnl_usd[1h])

# 查看订单成交率
rate(polybot_orders_filled_total[5m]) / rate(polybot_orders_placed_total[5m])
```

### 步骤 4: 使用 Graph 视图
1. 输入查询后，点击 **"Graph"** 标签
2. 可以查看指标随时间的变化趋势
3. 使用时间范围选择器调整显示的时间窗口

### 步骤 5: 查看告警规则
1. 点击 **"Alerts"** 查看配置的告警规则
2. 告警规则定义在 `monitoring/prometheus/alerts.yml`

---

## 三、开始使用交易策略和分析工具

### 3.1 检查服务状态

#### 查看所有服务健康状态
```bash
# Executor Service
curl http://localhost:8080/actuator/health

# Strategy Service
curl http://localhost:8081/actuator/health

# Analytics Service
curl http://localhost:8082/actuator/health

# Ingestor Service
curl http://localhost:8083/actuator/health
```

#### 查看服务指标端点
```bash
# 查看 Executor Service 的 Prometheus 指标
curl http://localhost:8080/actuator/prometheus | grep polybot

# 查看 Strategy Service 的指标
curl http://localhost:8081/actuator/prometheus | grep polybot
```

### 3.2 配置策略参数

#### 查看当前配置
配置文件位置: `strategy-service/src/main/resources/application-develop.yaml`

主要配置项:
- `hft.mode`: 交易模式 (`PAPER` 或 `LIVE`)
- `hft.strategy.gabagool.*`: Gabagool 策略参数
- `hft.risk.*`: 风险管理参数

#### 修改配置 (需要重启服务)
```bash
# 编辑配置文件
vim strategy-service/src/main/resources/application-develop.yaml

# 重启策略服务
./stop-all-services.sh
./start-all-services.sh
```

### 3.3 使用研究工具 (Python)

#### 步骤 1: 设置 Python 环境
```bash
cd research

# 使用 uv (推荐)
uv venv
uv pip install -r requirements.txt

# 或使用 venv
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

#### 步骤 2: 运行分析脚本

##### 2.1 数据快照报告
```bash
cd research
python snapshot_report.py
```
这将生成一个数据快照，包含:
- 交易统计
- 盈亏分析
- 市场覆盖情况

##### 2.2 深度分析
```bash
python deep_analysis.py
```
分析内容包括:
- 策略模式识别
- 入场/出场信号
- 持仓规模规则
- 时机模式

##### 2.3 复制评分
```bash
# 比较你的策略与目标用户的匹配度
python replication_score.py --candidate <your-username>

# 指定时间范围
python replication_score.py --candidate <your-username> \
  --start-ts '2025-01-04 00:00:00' \
  --end-ts '2025-01-04 23:59:59'
```

##### 2.4 模拟交易匹配报告
```bash
# 比较模拟交易与目标用户的执行
python sim_trade_match_report.py --hours 24

# 指定运行ID
python sim_trade_match_report.py --hours 6 --run-id <your-run-id>
```

##### 2.5 回测
```bash
cd research/backtest
python strategy_backtest.py
```

### 3.4 使用 Jupyter Notebook (推荐用于研究)

#### 启动 Jupyter Lab
```bash
cd research
uv run jupyter lab
# 或
jupyter lab
```

#### 打开研究笔记本
1. 浏览器会自动打开 Jupyter Lab (通常是 http://localhost:8888)
2. 打开以下笔记本 (按顺序):
   - `notebooks/01_extract_snapshot.ipynb` - 提取数据快照
   - `notebooks/02_feature_layer_and_regimes.ipynb` - 特征层和策略模式
   - `notebooks/03_model_and_tests.ipynb` - 模型和测试
   - `notebooks/04_backtest_and_montecarlo.ipynb` - 回测和蒙特卡洛模拟

### 3.5 查看 ClickHouse 数据

#### 访问 ClickHouse
```bash
# HTTP 接口
curl http://localhost:8123

# 或使用 ClickHouse 客户端
clickhouse-client --host localhost --port 9000
```

#### 常用查询示例
```sql
-- 查看用户交易数据
SELECT * FROM polybot.user_trade_research 
ORDER BY trade_timestamp DESC 
LIMIT 10;

-- 查看市场订单簿数据
SELECT * FROM polybot.clob_tob 
ORDER BY timestamp DESC 
LIMIT 10;

-- 查看策略订单
SELECT * FROM polybot.strategy_gabagool_orders 
ORDER BY created_at DESC 
LIMIT 10;

-- 查看执行器订单状态
SELECT * FROM polybot.executor_order_status 
ORDER BY created_at DESC 
LIMIT 10;
```

### 3.6 监控交易活动

#### 实时查看日志
```bash
# Executor Service 日志
tail -f logs/executor-service.log

# Strategy Service 日志
tail -f logs/strategy-service.log

# 查看所有服务日志
tail -f logs/*.log
```

#### 查看 API 端点
```bash
# 查看持仓
curl http://localhost:8080/api/polymarket/positions

# 查看结算计划
curl http://localhost:8080/api/polymarket/settlement/plan

# 查看策略状态
curl http://localhost:8081/api/strategy/status
```

---

## 四、常用操作命令总结

### 服务管理
```bash
# 启动所有服务
./start-all-services.sh

# 停止所有服务
./stop-all-services.sh

# 查看服务状态
ps aux | grep java
docker ps
```

### Docker 管理
```bash
# 查看 Docker 容器
docker ps

# 查看容器日志
docker logs polybot-clickhouse
docker logs polybot-redpanda
docker logs polybot-prometheus
docker logs polybot-grafana

# 重启容器
docker restart polybot-clickhouse
```

### 数据管理
```bash
# 应用 ClickHouse 初始化脚本
./scripts/clickhouse/apply-init.sh

# 备份数据 (可选)
docker exec polybot-clickhouse clickhouse-client --query "BACKUP DATABASE polybot TO Disk('backups', 'backup_$(date +%Y%m%d)')"
```

---

## 五、故障排查

### 服务无法启动
1. 检查端口是否被占用: `lsof -i :8080,8081,8082,8083`
2. 查看日志: `tail -f logs/*.log`
3. 检查 Docker 容器: `docker ps -a`

### Prometheus 无法收集指标
1. 检查服务健康状态: `curl http://localhost:8080/actuator/health`
2. 检查指标端点: `curl http://localhost:8080/actuator/prometheus`
3. 查看 Prometheus 配置: `monitoring/prometheus/prometheus.yml`

### Grafana 无法连接 Prometheus
1. 检查 Prometheus 是否运行: `curl http://localhost:9090/-/healthy`
2. 检查 Grafana 数据源配置: Grafana UI → Configuration → Data Sources

### ClickHouse 连接问题
1. 检查容器状态: `docker ps | grep clickhouse`
2. 测试连接: `curl http://localhost:8123`
3. 查看日志: `docker logs polybot-clickhouse`

---

## 六、下一步学习

1. **阅读策略文档**: `docs/EXAMPLE_STRATEGY_SPEC.md`
2. **研究指南**: `docs/STRATEGY_RESEARCH_GUIDE.md`
3. **贡献指南**: `CONTRIBUTING.md`
4. **项目 README**: `README.md`

---

## 七、重要提示

⚠️ **安全提醒**:
- 默认使用 `PAPER` (模拟交易) 模式
- 切换到 `LIVE` 模式前，请确保:
  - 已充分测试策略
  - 已设置风险限制
  - 已备份重要数据

⚠️ **数据隐私**:
- 不要提交包含真实交易数据的代码
- 使用环境变量管理敏感信息
- 定期检查 `.gitignore` 配置

⚠️ **性能优化**:
- 监控系统资源使用
- 定期清理旧数据
- 优化 ClickHouse 查询性能

---

**祝你使用愉快！如有问题，请查看日志或查阅文档。**
