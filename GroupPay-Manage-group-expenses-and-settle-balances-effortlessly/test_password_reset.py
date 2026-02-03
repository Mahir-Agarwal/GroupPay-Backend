import sys
import json
import uuid
import urllib.request
import urllib.parse
import urllib.error
import time

BASE_URL = "http://10.27.247.110:8081"

def log(msg):
    print(f"[TEST] {msg}", flush=True)

def request(method, endpoint, data=None):
    url = f"{BASE_URL}/{endpoint}"
    headers = {"Content-Type": "application/json"}
    try:
        body = json.dumps(data).encode('utf-8') if data else None
        
        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        with urllib.request.urlopen(req) as response:
            return {"status": response.status, "body": response.read().decode('utf-8')}
    except urllib.error.HTTPError as e:
        return {"status": e.code, "body": e.read().decode('utf-8')}
    except Exception as e:
        return {"status": 0, "body": str(e)}

def main():
    u = str(uuid.uuid4())[:6]
    email = f"reset_{u}@test.com"
    username = f"reset_{u}"
    pwd = "oldPassword123"
    
    # 1. Register
    log(f"Registering {email}...")
    reg_url = f"auth/register?email={email}&password={pwd}&username={username}"
    res = request("POST", reg_url)
    if res['status'] != 200:
        log(f"Registration failed: {res}")
        sys.exit(1)
        
    # 2. Login to verify old password
    log("Verifying old password...")
    login_res = request("POST", f"auth/login?email={email}&password={pwd}")
    if login_res['status'] != 200:
        log(f"Login with old password failed: {login_res}")
        sys.exit(1)
    log("Old password works.")
        
    # 3. Forgot Password
    log("Requesting password reset...")
    forgot_res = request("POST", f"auth/forgot-password?email={email}")
    if forgot_res['status'] != 200:
        log(f"Forgot password failed: {forgot_res}")
        sys.exit(1)
        
    token = forgot_res['body']
    log(f"Received Token: {token}")
    
    # 4. Reset Password
    new_pwd = "newPassword456"
    log(f"Resetting password to {new_pwd}...")
    reset_url = f"auth/reset-password?token={token}&newPassword={new_pwd}"
    reset_res = request("POST", reset_url)
    if reset_res['status'] != 200:
        log(f"Reset failed: {reset_res}")
        sys.exit(1)
    log("Password reset success.")
    
    # 5. Verify New Password
    log("Logging in with NEW password...")
    login_new = request("POST", f"auth/login?email={email}&password={new_pwd}")
    if login_new['status'] != 200:
        log(f"Login with NEW password failed: {login_new}")
        sys.exit(1)
    log("Login with NEW password SUCCESS.")
    
    # 6. Verify Old Password Fails
    log("Verifying OLD password fails...")
    login_old = request("POST", f"auth/login?email={email}&password={pwd}")
    if login_old['status'] == 200:
        log("FAIL: Old password still works!")
        sys.exit(1)
    log("Old password correctly rejected.")
    log("=== PASSWORD RESET TEST PASSED ===")

if __name__ == "__main__":
    main()
