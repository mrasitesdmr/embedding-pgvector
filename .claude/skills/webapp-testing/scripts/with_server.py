#!/usr/bin/env python3
"""
Bir veya daha fazla server başlatır, hazır olmalarını bekler, bir komut çalıştırır ve sonra temizler.

Kullanım:
    # Tek server
    python scripts/with_server.py --server "cd frontend && npm run dev" --port 5173 -- python automation.py

    # Birden fazla server (Spring Boot ilk açılışta yavaş olabilir: --timeout'u artır)
    python scripts/with_server.py --timeout 180 \
      --server "cd backend && ./mvnw spring-boot:run" --port 8080 \
      --server "cd frontend && npm run dev" --port 5173 \
      -- python test.py

Server çıktıları --log-dir altındaki dosyalara yazılır (varsayılan: geçici bir dizin).
"""

import argparse
import os
import signal
import socket
import subprocess
import sys
import tempfile
import time


def is_server_ready(port, process, timeout):
    """Port açılana kadar bekler; süreç erken ölürse hemen False döner."""
    start_time = time.time()
    while time.time() - start_time < timeout:
        if process.poll() is not None:
            return False
        try:
            with socket.create_connection(('localhost', port), timeout=1):
                return True
        except OSError:
            time.sleep(0.5)
    return False


def stop_process_group(process):
    """shell=True ile başlatılan komutun tüm alt süreçlerini (ör. mvnw -> java) durdurur."""
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait()
    except ProcessLookupError:
        pass


def main():
    parser = argparse.ArgumentParser(description='Run command with one or more servers')
    parser.add_argument('--server', action='append', dest='servers', required=True, help='Server command (can be repeated)')
    parser.add_argument('--port', action='append', dest='ports', type=int, required=True, help='Port for each server (must match --server count)')
    parser.add_argument('--timeout', type=int, default=60, help='Timeout in seconds per server (default: 60)')
    parser.add_argument('--log-dir', default=None, help='Directory for server logs (default: a new temp directory)')
    parser.add_argument('command', nargs=argparse.REMAINDER, help='Command to run after server(s) ready')

    args = parser.parse_args()

    if args.command and args.command[0] == '--':
        args.command = args.command[1:]

    if not args.command:
        print("Error: No command specified to run")
        sys.exit(1)

    if len(args.servers) != len(args.ports):
        print("Error: Number of --server and --port arguments must match")
        sys.exit(1)

    log_dir = args.log_dir or tempfile.mkdtemp(prefix='with_server_')
    os.makedirs(log_dir, exist_ok=True)

    server_processes = []
    log_files = []

    try:
        for index, (command, port) in enumerate(zip(args.servers, args.ports), start=1):
            log_path = os.path.join(log_dir, f'server-{index}-{port}.log')
            log_file = open(log_path, 'w')
            log_files.append(log_file)
            print(f"Starting server {index}/{len(args.servers)}: {command} (log: {log_path})")

            # Çıktı PIPE yerine dosyaya yazılır: okunmayan bir pipe dolunca server bloklanır.
            # start_new_session=True, temizlikte tüm süreç grubunun öldürülebilmesi içindir.
            process = subprocess.Popen(
                command,
                shell=True,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
            server_processes.append(process)

            print(f"Waiting for server on port {port}...")
            if not is_server_ready(port, process, args.timeout):
                raise RuntimeError(f"Server failed to start on port {port} within {args.timeout}s; see {log_path}")
            print(f"Server ready on port {port}")

        print(f"\nAll {len(server_processes)} server(s) ready")
        print(f"Running: {' '.join(args.command)}\n")
        result = subprocess.run(args.command)
        sys.exit(result.returncode)

    finally:
        print(f"\nStopping {len(server_processes)} server(s)...")
        for index, process in enumerate(server_processes, start=1):
            stop_process_group(process)
            print(f"Server {index} stopped")
        for log_file in log_files:
            log_file.close()
        print(f"All servers stopped (logs: {log_dir})")


if __name__ == '__main__':
    main()
