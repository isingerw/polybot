#!/usr/bin/env python3
"""
获取 Polymarket API 凭证脚本
使用 L1 认证（私钥）创建或派生 L2 认证所需的 API 凭证
"""

import asyncio
import os
import sys

try:
    from py_clob_client.client import ClobClient
except ImportError:
    print("错误: 缺少 py-clob-client 模块")
    print("请运行以下命令安装:")
    print("  pip install py-clob-client")
    print("  或")
    print("  python3 -m pip install py-clob-client")
    sys.exit(1)

def get_api_credentials():
    """获取 Polymarket API 凭证"""
    
    # 配置
    host = "https://clob.polymarket.com"
    chain_id = 137  # Polygon mainnet
    private_key = "0xb62ad5b57131bc09bf5182aaa90c5b153a55d7698fe6961238f81b04cd3a5859"
    
    # 移除 0x 前缀（如果需要）
    if private_key.startswith("0x"):
        private_key = private_key[2:]
    
    print("=" * 60)
    print("获取 Polymarket API 凭证")
    print("=" * 60)
    print(f"Host: {host}")
    print(f"Chain ID: {chain_id}")
    print(f"Private Key: {private_key[:10]}...{private_key[-10:]} (已隐藏中间部分)")
    print()
    
    try:
        # 创建 CLOB 客户端
        print("正在创建 CLOB 客户端...")
        client = ClobClient(
            host=host,
            chain_id=chain_id,
            key=private_key  # Signer enables L1 methods
        )
        print("✓ 客户端创建成功")
        print()
        
        # 尝试创建或派生凭证
        print("正在创建或派生 API 凭证...")
        try:
            # 使用 create_or_derive_api_creds 方法（同步方法）
            api_creds = client.create_or_derive_api_creds()
            print("✓ 成功获取凭证")
            print()
        except Exception as e:
            print(f"⚠ 创建/派生失败: {e}")
            print("尝试使用 create_api_key...")
            try:
                api_creds = client.create_api_key()
                print("✓ 成功创建新凭证")
                print()
            except Exception as e2:
                print(f"错误: {e2}")
                raise
        
        # 显示结果
        print("=" * 60)
        print("API 凭证获取成功！")
        print("=" * 60)
        print()
        print("请将以下内容添加到 .env 文件中：")
        print()
        print("# Polymarket API 凭证")
        print(f"POLYMARKET_API_KEY={api_creds.api_key}")
        print(f"POLYMARKET_API_SECRET={api_creds.api_secret}")
        print(f"POLYMARKET_API_PASSPHRASE={api_creds.api_passphrase}")
        print(f"POLYMARKET_API_NONCE=0")
        print()
        print("=" * 60)
        print("详细凭证信息：")
        print("=" * 60)
        print(f"API Key: {api_creds.api_key}")
        print(f"Secret: {api_creds.api_secret}")
        print(f"Passphrase: {api_creds.api_passphrase}")
        print()
        
        return api_creds
        
    except ImportError as e:
        print("错误: 缺少必要的 Python 包")
        print("请安装: pip install py-clob-client")
        print(f"详细错误: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"错误: {e}")
        print()
        print("可能的原因：")
        print("1. 私钥格式不正确")
        print("2. 网络连接问题")
        print("3. Polymarket API 服务不可用")
        print("4. 私钥对应的地址没有在 Polymarket 上登录过")
        sys.exit(1)

if __name__ == "__main__":
    # 运行函数
    get_api_credentials()

