#!/usr/bin/env bash

# Polybot 服务测试脚本
# 用于验证所有服务是否正常运行

set -e

echo "=========================================="
echo "Polybot 服务健康检查"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查服务函数
check_service() {
    local name=$1
    local url=$2
    local expected_status=${3:-200}
    
    echo -n "检查 $name... "
    
    if response=$(curl -s -w "\n%{http_code}" "$url" 2>/dev/null); then
        http_code=$(echo "$response" | tail -n1)
        if [ "$http_code" = "$expected_status" ] || [ "$http_code" = "200" ]; then
            echo -e "${GREEN}✓ 运行中${NC} (HTTP $http_code)"
            return 0
        else
            echo -e "${YELLOW}⚠ 响应异常${NC} (HTTP $http_code)"
            return 1
        fi
    else
        echo -e "${RED}✗ 无法连接${NC}"
        return 1
    fi
}

# 检查端口是否监听
check_port() {
    local port=$1
    local name=$2
    
    echo -n "检查 $name (端口 $port)... "
    
    if lsof -i :$port > /dev/null 2>&1 || nc -z localhost $port 2>/dev/null; then
        echo -e "${GREEN}✓ 端口已监听${NC}"
        return 0
    else
        echo -e "${RED}✗ 端口未监听${NC}"
        return 1
    fi
}

# 统计
total=0
passed=0

echo "=== Spring Boot 服务 ==="
echo ""

# 检查 Spring Boot 服务
services=(
    "Executor Service:http://localhost:8080/actuator/health"
    "Strategy Service:http://localhost:8081/actuator/health"
    "Analytics Service:http://localhost:8082/actuator/health"
    "Ingestor Service:http://localhost:8083/actuator/health"
    "Infrastructure Orchestrator:http://localhost:8084/actuator/health"
)

for service in "${services[@]}"; do
    IFS=':' read -r name url <<< "$service"
    total=$((total + 1))
    if check_service "$name" "$url"; then
        passed=$((passed + 1))
    fi
done

echo ""
echo "=== 基础设施服务 ==="
echo ""

# 检查基础设施
infra_services=(
    "ClickHouse:8123"
    "RedPanda Kafka:9092"
    "RedPanda Admin:9644"
)

for service in "${infra_services[@]}"; do
    IFS=':' read -r name port <<< "$service"
    total=$((total + 1))
    if check_port "$port" "$name"; then
        passed=$((passed + 1))
    fi
done

echo ""
echo "=== 详细检查 ==="
echo ""

# 检查 ClickHouse 连接（使用密码）
echo -n "测试 ClickHouse 连接... "
if result=$(curl -s -u "default:ylzins2025" "http://localhost:8123/?query=SELECT%201" 2>/dev/null); then
    if [ -n "$result" ] && echo "$result" | grep -q "1"; then
        echo -e "${GREEN}✓ 连接成功${NC}"
        passed=$((passed + 1))
    elif [ -n "$result" ]; then
        echo -e "${YELLOW}⚠ 连接成功但响应异常: ${result:0:50}${NC}"
    else
        echo -e "${YELLOW}⚠ 连接成功但无响应${NC}"
    fi
else
    echo -e "${RED}✗ 连接失败${NC}"
fi
total=$((total + 1))

# 检查 Executor Service API
echo -n "测试 Executor Service API... "
if response=$(curl -s http://localhost:8080/api/polymarket/health 2>/dev/null); then
    if echo "$response" | grep -q "mode\|PAPER\|LIVE" 2>/dev/null; then
        mode=$(echo "$response" | grep -o '"mode":"[^"]*"' | cut -d'"' -f4)
        echo -e "${GREEN}✓ API 正常 (模式: ${mode})${NC}"
        passed=$((passed + 1))
    else
        echo -e "${YELLOW}⚠ API 响应异常${NC}"
    fi
else
    echo -e "${RED}✗ API 无法访问${NC}"
fi
total=$((total + 1))

# 检查模拟交易模式（通过 API）
echo -n "检查交易模式... "
if response=$(curl -s http://localhost:8080/api/polymarket/health 2>/dev/null); then
    mode=$(echo "$response" | grep -o '"mode":"[^"]*"' | cut -d'"' -f4)
    if [ "$mode" = "PAPER" ]; then
        echo -e "${GREEN}✓ 模拟交易模式已启用 (PAPER)${NC}"
        passed=$((passed + 1))
    elif [ "$mode" = "LIVE" ]; then
        echo -e "${YELLOW}⚠ 实盘交易模式 (LIVE)${NC}"
    else
        echo -e "${YELLOW}⚠ 未知模式: ${mode}${NC}"
    fi
else
    echo -e "${YELLOW}⚠ 无法检查交易模式${NC}"
fi
total=$((total + 1))

# 检查策略执行
echo -n "检查策略服务... "
if logs=$(tail -100 logs/strategy-service.log 2>/dev/null | grep -i "GABAGOOL\|started\|running" | head -1); then
    if [ -n "$logs" ]; then
        echo -e "${GREEN}✓ 策略服务运行中${NC}"
        passed=$((passed + 1))
    else
        echo -e "${YELLOW}⚠ 未找到策略服务日志${NC}"
    fi
else
    echo -e "${YELLOW}⚠ 无法读取日志文件${NC}"
fi
total=$((total + 1))

echo ""
echo "=========================================="
echo "测试结果: $passed/$total 通过"
echo "=========================================="
echo ""

if [ $passed -eq $total ]; then
    echo -e "${GREEN}✓ 所有服务运行正常！${NC}"
    echo ""
    echo "下一步："
    echo "  1. 查看交易日志: tail -f logs/executor-service.log"
    echo "  2. 查看策略日志: tail -f logs/strategy-service.log"
    echo "  3. 检查持仓: curl http://localhost:8080/api/polymarket/positions"
    exit 0
else
    echo -e "${YELLOW}⚠ 部分服务可能未正常运行${NC}"
    echo ""
    echo "排查建议："
    echo "  1. 检查服务日志: tail -f logs/*.log"
    echo "  2. 检查端口占用: lsof -i :8080-8084"
    echo "  3. 检查 Docker 容器: docker ps"
    exit 1
fi

