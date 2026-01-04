#!/usr/bin/env bash

# 持仓分析脚本
# 用于分析当前持仓情况

echo "=========================================="
echo "Polybot 持仓分析"
echo "=========================================="
echo ""

# 获取持仓数据
POSITIONS=$(curl -s http://localhost:8080/api/polymarket/positions)

if [ -z "$POSITIONS" ] || [ "$POSITIONS" = "[]" ]; then
    echo "当前没有持仓"
    exit 0
fi

# 检查是否有 jq
if command -v jq &> /dev/null; then
    echo "=== 持仓概览 ==="
    echo ""
    
    # 统计信息
    TOTAL_POSITIONS=$(echo "$POSITIONS" | jq 'length')
    echo "总持仓数: $TOTAL_POSITIONS"
    echo ""
    
    # 按市场分组
    echo "=== 按市场分组 ==="
    echo ""
    echo "$POSITIONS" | jq -r 'group_by(.slug) | .[] | "市场: \(.[0].title)\n  持仓数: \(length)\n"'
    
    # 持仓详情
    echo "=== 持仓详情 ==="
    echo ""
    echo "$POSITIONS" | jq -r '.[] | 
        "市场: \(.title)
  方向: \(.outcome)
  数量: \(.size) 股
  平均价格: $\(.avgPrice)
  当前价格: $\(.curPrice // "N/A")
  初始价值: $\(.initialValue)
  条件ID: \(.conditionId[0:20])...
  ---"'
    
    # 计算总价值
    echo ""
    echo "=== 价值统计 ==="
    TOTAL_INITIAL=$(echo "$POSITIONS" | jq '[.[] | .initialValue] | add')
    echo "总初始价值: \$$(printf "%.2f" $TOTAL_INITIAL)"
    
    # 按市场统计
    echo ""
    echo "=== 按市场统计 ==="
    echo "$POSITIONS" | jq -r 'group_by(.slug) | .[] | 
        "\(.[0].title):
  UP持仓: \(map(select(.outcome == "Up")) | length) 个
  DOWN持仓: \(map(select(.outcome == "Down")) | length) 个
  总价值: $\(map(.initialValue) | add | .)
  ---"'
    
else
    # 如果没有 jq，使用 Python
    python3 << 'PYTHON_SCRIPT'
import json
import sys

try:
    positions = json.load(sys.stdin)
    
    if not positions:
        print("当前没有持仓")
        sys.exit(0)
    
    print(f"总持仓数: {len(positions)}")
    print()
    
    # 按市场分组
    markets = {}
    for pos in positions:
        slug = pos.get('slug', 'unknown')
        if slug not in markets:
            markets[slug] = {'title': pos.get('title', 'Unknown'), 'positions': []}
        markets[slug]['positions'].append(pos)
    
    print("=== 按市场分组 ===")
    print()
    for slug, data in markets.items():
        print(f"市场: {data['title']}")
        print(f"  持仓数: {len(data['positions'])}")
        up_count = sum(1 for p in data['positions'] if p.get('outcome') == 'Up')
        down_count = sum(1 for p in data['positions'] if p.get('outcome') == 'Down')
        total_value = sum(p.get('initialValue', 0) for p in data['positions'])
        print(f"  UP持仓: {up_count} 个")
        print(f"  DOWN持仓: {down_count} 个")
        print(f"  总价值: ${total_value:.2f}")
        print()
    
    print("=== 持仓详情 ===")
    print()
    for pos in positions:
        print(f"市场: {pos.get('title', 'Unknown')}")
        print(f"  方向: {pos.get('outcome', 'Unknown')}")
        print(f"  数量: {pos.get('size', 0)} 股")
        print(f"  平均价格: ${pos.get('avgPrice', 0)}")
        print(f"  当前价格: ${pos.get('curPrice', 'N/A')}")
        print(f"  初始价值: ${pos.get('initialValue', 0)}")
        print("  ---")
    
    total_initial = sum(p.get('initialValue', 0) for p in positions)
    print()
    print(f"总初始价值: ${total_initial:.2f}")
    
except Exception as e:
    print(f"错误: {e}")
    sys.exit(1)
PYTHON_SCRIPT
fi

echo ""
echo "=========================================="
echo "分析完成"
echo "=========================================="

