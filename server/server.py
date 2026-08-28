#!/usr/bin/env python3
"""
LifeOS Full-Stack Backend Server in Python
Serves static HTML frontend + REST API for Authentication & Data Sync
"""

import http.server
import socketserver
import json
import os
import re
import time
from urllib.parse import urlparse, parse_qs

PORT = int(os.environ.get('PORT', 9090))
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
DATA_DIR = os.path.join(os.path.dirname(__file__), 'data')

os.makedirs(DATA_DIR, exist_ok=True)
USERS_FILE = os.path.join(DATA_DIR, 'users.json')
SYNC_FILE = os.path.join(DATA_DIR, 'sync_data.json')

def load_json(filepath, default):
    if os.path.exists(filepath):
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception:
            return default
    return default

def save_json(filepath, data):
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)

class LifeOSHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def send_json(self, status, payload):
        body = json.dumps(payload).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip('/')

        # API: Health Check
        if path == '/health':
            return self.send_json(200, {
                'status': 'ok',
                'service': 'LifeOS Python Server',
                'timestamp': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
            })

        # API: Fetch User Sync Data
        if path.startswith('/api/sync/'):
            user_id = path.replace('/api/sync/', '')
            sync_db = load_json(SYNC_FILE, {})
            if user_id in sync_db:
                return self.send_json(200, {
                    'success': True,
                    'userId': user_id,
                    'lastSynced': sync_db[user_id].get('lastSynced'),
                    'version': sync_db[user_id].get('version', '1.0'),
                    'data': sync_db[user_id].get('data', {})
                })
            return self.send_json(404, {'error': 'User sync data not found', 'userId': user_id})

        # Static File Route clean aliases
        routes = {
            '': 'index.html',
            '/index': 'index.html',
            '/login': 'login-signup.html',
            '/login-signup': 'login-signup.html',
            '/blogs': 'blogs.html',
            '/about': 'about.html',
            '/privacy': 'privacy.html',
            '/community': 'community.html',
            '/accessibility': 'accessibility.html'
        }

        if path in routes:
            self.path = '/' + routes[path]

        return super().do_GET()

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip('/')

        length = int(self.headers.get('Content-Length', 0))
        raw_body = self.rfile.read(length) if length > 0 else b'{}'
        try:
            body = json.loads(raw_body.decode('utf-8'))
        except Exception:
            body = {}

        # API: Login
        if path == '/api/auth/login':
            login = (body.get('login') or body.get('email') or body.get('mobile') or '').strip()
            password = body.get('password', '')

            if not login:
                return self.send_json(400, {'success': False, 'error': 'Email or mobile number is required.'})

            norm_login = login.lower()
            digits = re.sub(r'\D', '', login)

            # Demo User Account
            if 'siddhartha' in norm_login or norm_login == 'siddhartha12495@gmail.com' or digits == '9876543210':
                return self.send_json(200, {
                    'success': True,
                    'user': {
                        'id': 'usr_demo_siddhartha',
                        'name': 'Siddhartha',
                        'email': 'siddhartha12495@gmail.com',
                        'mobile': '9876543210',
                        'avatar': 'https://api.dicebear.com/7.x/avataaars/svg?seed=Siddhartha'
                    },
                    'token': 'jwt_demo_siddhartha'
                })

            users = load_json(USERS_FILE, [])
            found = None
            for u in users:
                if u.get('email') == norm_login or u.get('mobile') == digits or u.get('name', '').lower() == norm_login:
                    found = u
                    break

            if found:
                if found.get('password') and found['password'] != password:
                    return self.send_json(401, {'success': False, 'error': 'Invalid password. Please check your credentials.'})
                return self.send_json(200, {
                    'success': True,
                    'user': {
                        'id': found['id'],
                        'name': found['name'],
                        'email': found.get('email'),
                        'mobile': found.get('mobile'),
                        'avatar': found.get('avatar')
                    },
                    'token': 'jwt_' + found['id']
                })

            # Auto-register new user on first login
            name = login.split('@')[0].capitalize() if '@' in login else 'Member'
            new_user = {
                'id': 'usr_' + str(int(time.time())) + '_' + os.urandom(3).hex(),
                'name': name,
                'email': norm_login if '@' in norm_login else None,
                'mobile': digits if not '@' in norm_login else None,
                'password': password,
                'avatar': f'https://api.dicebear.com/7.x/avataaars/svg?seed={name}'
            }
            users.append(new_user)
            save_json(USERS_FILE, users)

            return self.send_json(200, {
                'success': True,
                'user': {
                    'id': new_user['id'],
                    'name': new_user['name'],
                    'email': new_user['email'],
                    'mobile': new_user['mobile'],
                    'avatar': new_user['avatar']
                },
                'token': 'jwt_' + new_user['id']
            })

        # API: Register
        if path == '/api/auth/register':
            name = body.get('name', '').strip()
            email = (body.get('email') or '').strip().lower()
            mobile = re.sub(r'\D', '', body.get('mobile', ''))
            password = body.get('password', '')

            if not name or (not email and not mobile):
                return self.send_json(400, {'success': False, 'error': 'Name and email/mobile are required.'})

            users = load_json(USERS_FILE, [])
            user_id = 'usr_' + str(int(time.time())) + '_' + os.urandom(3).hex()
            user = {
                'id': user_id,
                'name': name,
                'email': email or None,
                'mobile': mobile or None,
                'password': password,
                'avatar': f'https://api.dicebear.com/7.x/avataaars/svg?seed={name}',
                'created_at': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
            }
            users.append(user)
            save_json(USERS_FILE, users)

            return self.send_json(200, {
                'success': True,
                'user': {
                    'id': user['id'],
                    'name': user['name'],
                    'email': user['email'],
                    'mobile': user['mobile'],
                    'avatar': user['avatar']
                },
                'token': 'jwt_' + user['id']
            })

        # API: Sync Push
        if path.startswith('/api/sync/'):
            user_id = path.replace('/api/sync/', '')
            data = body.get('data')
            version = body.get('version', '1.0')

            sync_db = load_json(SYNC_FILE, {})
            sync_db[user_id] = {
                'data': data,
                'version': version,
                'lastSynced': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
            }
            save_json(SYNC_FILE, sync_db)

            return self.send_json(200, {
                'success': True,
                'userId': user_id,
                'message': 'LifeOS data synced successfully'
            })

        return self.send_json(404, {'error': 'Endpoint not found'})

if __name__ == '__main__':
    with socketserver.TCPServer(('', PORT), LifeOSHandler) as httpd:
        print(f"🚀 LifeOS Full-Stack Server running on http://localhost:{PORT}")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down server.")
