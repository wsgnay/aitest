#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
FFmpeg视频处理服务API压测脚本

此脚本用于压测FFmpeg视频处理服务的媒体信息接口，并生成性能报告。
支持多线程并发请求，可配置并发用户数、请求总数和测试持续时间等参数。
"""

import argparse
import time
import json
import statistics
import threading
import requests
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
import matplotlib.pyplot as plt
import numpy as np
import os
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

class FFmpegAPIBenchmark:
    """FFmpeg API压测类"""
    
    def __init__(self, base_url, file_path, num_users, total_requests=None, 
                 duration=None, ramp_up=0, timeout=10, output_dir="results"):
        """
        初始化压测参数
        
        Args:
            base_url (str): API基础URL
            file_path (str): 视频文件路径
            num_users (int): 并发用户数
            total_requests (int, optional): 总请求数，与duration二选一
            duration (int, optional): 测试持续时间(秒)，与total_requests二选一
            ramp_up (int, optional): 用户启动时间(秒)
            timeout (int, optional): 请求超时时间(秒)
            output_dir (str, optional): 结果输出目录
        """
        self.base_url = base_url
        self.file_path = file_path
        self.num_users = num_users
        self.total_requests = total_requests
        self.duration = duration
        self.ramp_up = ramp_up
        self.timeout = timeout
        self.output_dir = output_dir
        
        # 确保输出目录存在
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)
        
        # 结果统计
        self.results = {
            "response_times": [],
            "status_codes": {},
            "errors": {},
            "start_time": None,
            "end_time": None,
            "successful_requests": 0,
            "failed_requests": 0
        }
        
        # 线程安全锁
        self.lock = threading.Lock()
        
        # 停止标志
        self.stop_event = threading.Event()
        
        # 创建会话对象，启用连接池
        self.session = requests.Session()
        
        # 配置重试策略
        retry_strategy = Retry(
            total=3,
            backoff_factor=0.5,
            status_forcelist=[429, 500, 502, 503, 504],
            allowed_methods=["GET"]
        )
        adapter = HTTPAdapter(max_retries=retry_strategy, pool_connections=num_users, pool_maxsize=num_users)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)
    
    def make_request(self):
        """发送单个API请求并记录结果"""
        url = f"{self.base_url}/api/media/info"
        params = {"filePath": self.file_path}
        
        start_time = time.time()
        try:
            response = self.session.get(url, params=params, timeout=self.timeout)
            response_time = time.time() - start_time
            status_code = response.status_code
            
            with self.lock:
                self.results["response_times"].append(response_time)
                
                if status_code in self.results["status_codes"]:
                    self.results["status_codes"][status_code] += 1
                else:
                    self.results["status_codes"][status_code] = 1
                
                if 200 <= status_code < 400:
                    self.results["successful_requests"] += 1
                else:
                    self.results["failed_requests"] += 1
                    error_msg = f"HTTP {status_code}"
                    if error_msg in self.results["errors"]:
                        self.results["errors"][error_msg] += 1
                    else:
                        self.results["errors"][error_msg] = 1
            
            return response_time, status_code
            
        except requests.exceptions.RequestException as e:
            response_time = time.time() - start_time
            error_type = type(e).__name__
            
            with self.lock:
                self.results["response_times"].append(response_time)
                self.results["failed_requests"] += 1
                
                if error_type in self.results["errors"]:
                    self.results["errors"][error_type] += 1
                else:
                    self.results["errors"][error_type] = 1
            
            return response_time, f"Error: {error_type}"
    
    def user_thread(self, user_id):
        """模拟单个用户的行为"""
        # 实现用户启动时间
        if self.ramp_up > 0:
            delay = (self.ramp_up / self.num_users) * user_id
            time.sleep(delay)
        
        # 基于总请求数的测试
        if self.total_requests is not None:
            requests_per_user = self.total_requests // self.num_users
            for _ in range(requests_per_user):
                if self.stop_event.is_set():
                    break
                self.make_request()
        
        # 基于持续时间的测试
        else:
            while not self.stop_event.is_set():
                self.make_request()
    
    def run(self):
        """运行压测"""
        print(f"开始压测 {self.base_url}/api/media/info")
        print(f"并发用户数: {self.num_users}")
        if self.total_requests:
            print(f"总请求数: {self.total_requests}")
        if self.duration:
            print(f"测试持续时间: {self.duration}秒")
        print(f"用户启动时间: {self.ramp_up}秒")
        print(f"请求超时时间: {self.timeout}秒")
        print(f"视频文件路径: {self.file_path}")
        print("=" * 50)
        
        self.results["start_time"] = datetime.now()
        
        # 创建并启动用户线程
        with ThreadPoolExecutor(max_workers=self.num_users) as executor:
            futures = [executor.submit(self.user_thread, i) for i in range(self.num_users)]
            
            # 如果设置了持续时间，等待指定时间后停止
            if self.duration:
                time.sleep(self.duration)
                self.stop_event.set()
            
            # 等待所有线程完成
            for future in futures:
                future.result()
        
        self.results["end_time"] = datetime.now()
        
        # 生成报告
        self.generate_report()
    
    def generate_report(self):
        """生成测试报告"""
        # 计算测试时间
        test_duration = (self.results["end_time"] - self.results["start_time"]).total_seconds()
        
        # 计算统计数据
        total_requests = self.results["successful_requests"] + self.results["failed_requests"]
        rps = total_requests / test_duration if test_duration > 0 else 0
        
        response_times = self.results["response_times"]
        if response_times:
            avg_response_time = statistics.mean(response_times)
            min_response_time = min(response_times)
            max_response_time = max(response_times)
            p50_response_time = np.percentile(response_times, 50)
            p90_response_time = np.percentile(response_times, 90)
            p95_response_time = np.percentile(response_times, 95)
            p99_response_time = np.percentile(response_times, 99)
        else:
            avg_response_time = min_response_time = max_response_time = 0
            p50_response_time = p90_response_time = p95_response_time = p99_response_time = 0
        
        # 控制台输出
        print("\n" + "=" * 50)
        print("测试结果摘要:")
        print(f"测试开始时间: {self.results['start_time'].strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"测试结束时间: {self.results['end_time'].strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"测试持续时间: {test_duration:.2f}秒")
        print(f"总请求数: {total_requests}")
        print(f"成功请求数: {self.results['successful_requests']}")
        print(f"失败请求数: {self.results['failed_requests']}")
        print(f"请求成功率: {(self.results['successful_requests'] / total_requests * 100) if total_requests > 0 else 0:.2f}%")
        print(f"平均每秒请求数 (RPS): {rps:.2f}")
        print("\n响应时间统计 (秒):")
        print(f"  最小: {min_response_time:.4f}")
        print(f"  平均: {avg_response_time:.4f}")
        print(f"  最大: {max_response_time:.4f}")
        print(f"  中位数 (P50): {p50_response_time:.4f}")
        print(f"  P90: {p90_response_time:.4f}")
        print(f"  P95: {p95_response_time:.4f}")
        print(f"  P99: {p99_response_time:.4f}")
        
        if self.results["status_codes"]:
            print("\nHTTP状态码分布:")
            for code, count in sorted(self.results["status_codes"].items()):
                print(f"  {code}: {count} ({count/total_requests*100:.2f}%)")
        
        if self.results["errors"]:
            print("\n错误分布:")
            for error, count in sorted(self.results["errors"].items(), key=lambda x: x[1], reverse=True):
                print(f"  {error}: {count} ({count/total_requests*100:.2f}%)")
        
        # 生成图表
        self._generate_charts()
        
        # 保存JSON报告
        self._save_json_report(test_duration, total_requests, rps, avg_response_time, 
                              min_response_time, max_response_time, 
                              p50_response_time, p90_response_time, p95_response_time, p99_response_time)
        
        print(f"\n报告已保存到 {self.output_dir} 目录")
    
    def _generate_charts(self):
        """生成图表"""
        # 响应时间分布图
        plt.figure(figsize=(10, 6))
        plt.hist(self.results["response_times"], bins=50, alpha=0.75)
        plt.title('响应时间分布')
        plt.xlabel('响应时间 (秒)')
        plt.ylabel('请求数')
        plt.grid(True, linestyle='--', alpha=0.7)
        plt.savefig(f"{self.output_dir}/response_time_histogram.png")
        
        # HTTP状态码饼图
        if self.results["status_codes"]:
            plt.figure(figsize=(10, 6))
            labels = [f"HTTP {code}" for code in self.results["status_codes"].keys()]
            sizes = list(self.results["status_codes"].values())
            plt.pie(sizes, labels=labels, autopct='%1.1f%%', startangle=90)
            plt.axis('equal')
            plt.title('HTTP状态码分布')
            plt.savefig(f"{self.output_dir}/status_code_pie.png")
    
    def _save_json_report(self, test_duration, total_requests, rps, avg_response_time, 
                         min_response_time, max_response_time, 
                         p50_response_time, p90_response_time, p95_response_time, p99_response_time):
        """保存JSON格式的报告"""
        report = {
            "test_info": {
                "base_url": self.base_url,
                "file_path": self.file_path,
                "num_users": self.num_users,
                "total_requests": self.total_requests,
                "duration": self.duration,
                "ramp_up": self.ramp_up,
                "timeout": self.timeout,
                "start_time": self.results["start_time"].strftime("%Y-%m-%d %H:%M:%S"),
                "end_time": self.results["end_time"].strftime("%Y-%m-%d %H:%M:%S"),
                "test_duration": test_duration
            },
            "summary": {
                "total_requests": total_requests,
                "successful_requests": self.results["successful_requests"],
                "failed_requests": self.results["failed_requests"],
                "success_rate": (self.results["successful_requests"] / total_requests * 100) if total_requests > 0 else 0,
                "requests_per_second": rps
            },
            "response_times": {
                "min": min_response_time,
                "avg": avg_response_time,
                "max": max_response_time,
                "p50": p50_response_time,
                "p90": p90_response_time,
                "p95": p95_response_time,
                "p99": p99_response_time
            },
            "status_codes": self.results["status_codes"],
            "errors": self.results["errors"]
        }
        
        timestamp = self.results["start_time"].strftime("%Y%m%d_%H%M%S")
        with open(f"{self.output_dir}/report_{timestamp}.json", "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        
        # 生成Markdown报告
        self._generate_markdown_report(report, timestamp)
    
    def _generate_markdown_report(self, report, timestamp):
        """生成Markdown格式的报告"""
        md_content = f"""# FFmpeg视频处理服务API压测报告

## 测试信息

- **测试URL**: {report['test_info']['base_url']}/api/media/info
- **视频文件**: {report['test_info']['file_path']}
- **并发用户数**: {report['test_info']['num_users']}
- **测试开始时间**: {report['test_info']['start_time']}
- **测试结束时间**: {report['test_info']['end_time']}
- **测试持续时间**: {report['test_info']['test_duration']:.2f}秒
- **用户启动时间**: {report['test_info']['ramp_up']}秒
- **请求超时时间**: {report['test_info']['timeout']}秒

## 测试结果摘要

- **总请求数**: {report['summary']['total_requests']}
- **成功请求数**: {report['summary']['successful_requests']}
- **失败请求数**: {report['summary']['failed_requests']}
- **请求成功率**: {report['summary']['success_rate']:.2f}%
- **平均每秒请求数 (RPS)**: {report['summary']['requests_per_second']:.2f}

## 响应时间统计 (秒)

| 指标 | 响应时间 |
|------|----------|
| 最小 | {report['response_times']['min']:.4f} |
| 平均 | {report['response_times']['avg']:.4f} |
| 最大 | {report['response_times']['max']:.4f} |
| 中位数 (P50) | {report['response_times']['p50']:.4f} |
| P90 | {report['response_times']['p90']:.4f} |
| P95 | {report['response_times']['p95']:.4f} |
| P99 | {report['response_times']['p99']:.4f} |

## HTTP状态码分布

"""
        
        if report["status_codes"]:
            md_content += "| 状态码 | 请求数 | 百分比 |\n|--------|--------|--------|\n"
            total = report['summary']['total_requests']
            for code, count in sorted(report["status_codes"].items()):
                md_content += f"| {code} | {count} | {count/total*100:.2f}% |\n"
        else:
            md_content += "无HTTP状态码数据\n"
        
        md_content += "\n## 错误分布\n\n"
        
        if report["errors"]:
            md_content += "| 错误类型 | 请求数 | 百分比 |\n|--------|--------|--------|\n"
            total = report['summary']['total_requests']
            for error, count in sorted(report["errors"].items(), key=lambda x: x[1], reverse=True):
                md_content += f"| {error} | {count} | {count/total*100:.2f}% |\n"
        else:
            md_content += "无错误数据\n"
        
        md_content += "\n## 图表\n\n"
        md_content += "### 响应时间分布\n\n"
        md_content += "![响应时间分布](./response_time_histogram.png)\n\n"
        
        if report["status_codes"]:
            md_content += "### HTTP状态码分布\n\n"
            md_content += "![HTTP状态码分布](./status_code_pie.png)\n"
        
        with open(f"{self.output_dir}/report_{timestamp}.md", "w", encoding="utf-8") as f:
            f.write(md_content)


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="FFmpeg视频处理服务API压测工具")
    parser.add_argument("--url", required=True, help="API基础URL，例如: http://example.com")
    parser.add_argument("--file", required=True, help="视频文件路径")
    parser.add_argument("--users", type=int, default=10, help="并发用户数")
    parser.add_argument("--requests", type=int, help="总请求数")
    parser.add_argument("--duration", type=int, help="测试持续时间(秒)")
    parser.add_argument("--ramp-up", type=int, default=0, help="用户启动时间(秒)")
    parser.add_argument("--timeout", type=int, default=10, help="请求超时时间(秒)")
    parser.add_argument("--output", default="results", help="结果输出目录")
    
    args = parser.parse_args()
    
    if args.requests is None and args.duration is None:
        parser.error("必须指定 --requests 或 --duration 参数之一")
    
    benchmark = FFmpegAPIBenchmark(
        base_url=args.url,
        file_path=args.file,
        num_users=args.users,
        total_requests=args.requests,
        duration=args.duration,
        ramp_up=args.ramp_up,
        timeout=args.timeout,
        output_dir=args.output
    )
    
    benchmark.run()


if __name__ == "__main__":
    main() 