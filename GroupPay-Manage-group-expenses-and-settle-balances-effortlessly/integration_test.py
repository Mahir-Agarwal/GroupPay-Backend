
import sys
import json
import uuid
import urllib.request
import urllib.error
import urllib.parse
import base64

BASE_URL = "http://10.27.247.110:8081"

def log(msg, type="INFO"):
    print(f"[{type}] {msg}", flush=True)

def fail(msg):
    log(msg, "FAIL")
    sys.exit(1)

def decode_jwt(token):
    try:
        parts = token.split('.')
        if len(parts) != 3:
            return "Invalid Token"
        payload = parts[1]
        padded = payload + '=' * (4 - len(payload) % 4)
        decoded = base64.b64decode(padded).decode('utf-8')
        return json.loads(decoded)
    except Exception as e:
        return f"Decode Error: {e}"

def request(method, endpoint, data=None, token=None):
    url = f"{BASE_URL}/{endpoint}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    try:
        if data:
            json_data = json.dumps(data).encode('utf-8')
            req = urllib.request.Request(url, data=json_data, headers=headers, method=method)
        else:
            req = urllib.request.Request(url, headers=headers, method=method)
            
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode('utf-8')
            # DEBUG
            # print(f"DEBUG RESPONSE: {res_body}")
            parsed_body = {}
            if res_body and res_body.strip():
                try:
                    parsed_body = json.loads(res_body)
                except:
                    parsed_body = res_body
            return {"status": response.status, "body": parsed_body}
            
    except urllib.error.HTTPError as e:
        res_body = e.read().decode('utf-8')
        json_body = res_body
        if res_body and res_body.strip():
             try:
                 json_body = json.loads(res_body)
             except:
                 pass
        return {"status": e.code, "body": json_body, "error": str(e)}
    except urllib.error.URLError as e:
        fail(f"Network Error ({url}): {e}")
    except Exception as e:
        fail(f"Unexpected Error: {e}")

def register_user(username, email, password):
    log(f"Registering {username} ({email})...")
    
    email_enc = urllib.parse.quote(email)
    pass_enc = urllib.parse.quote(password)
    user_enc = urllib.parse.quote(username)
    
    res = request("POST", f"auth/register?email={email_enc}&password={pass_enc}&username={user_enc}")
    
    if res['status'] in [200, 201]:
        log("Registration Successful.")
        return res['body']
    elif res['status'] == 400 or (isinstance(res['body'], str) and "exists" in str(res['body'])):
         log(f"User {email} already exists (or error). Proceeding to login.")
         return login_user(email, password)
    else:
        fail(f"Registration Failed: {res['status']} {res['body']}")

def login_user(email, password):
    log(f"Logging in {email}...")
    email_enc = urllib.parse.quote(email)
    pass_enc = urllib.parse.quote(password)
    
    res = request("POST", f"auth/login?email={email_enc}&password={pass_enc}")
    
    if res['status'] == 200:
        log("Login Successful.")
        return res['body']
    else:
        fail(f"Login Failed: {res['status']} {res['body']}")

def add_member(token, group_id, user_id):
    # API: POST /groups/{groupId}/members?userId={userId}
    request("POST", f"groups/{group_id}/members?userId={user_id}", token=token)

def create_group(token, name, user_ids):
    log(f"Creating Group '{name}' with members {user_ids}...")
    
    # Needs creatorId (Long)
    creator_id = user_ids[0]
    name_encoded = urllib.parse.quote(name)
    
    res = request("POST", f"groups?name={name_encoded}&creatorId={creator_id}", token=token)
    
    if res['status'] in [200, 201]:
        group_id = res['body']['id']
        log(f"Group Created: ID={group_id}")
        
        # Manually add other members
        for uid in user_ids[1:]:
             log(f"Adding member {uid}...")
             add_member(token, group_id, uid)
             
        return res['body']
    else:
        fail(f"Create Group Failed: {res['status']} {res['body']}")

def add_expense(token, group_id, description, amount, payer_id, split_type, splits=None):
    log(f"Adding Expense: {description} (${amount}) [{split_type}]...")
    
    desc_encoded = urllib.parse.quote(description)
    url_params = f"userId={payer_id}&groupId={group_id}&description={desc_encoded}&amount={amount}&type={split_type}"
    target_url = f"expenses?{url_params}"
    
    data = None
    if splits:
        data = splits
        
    res = request("POST", target_url, data, token)
    
    if res['status'] in [200, 201]:
        log(f"Expense Added: ID={res['body']['id']}")
        return res['body']
    else:
        return res

def get_group_expenses(token, group_id):
    res = request("GET", f"expenses/group/{group_id}", token=token)
    if res['status'] == 200:
        return res['body']
    else:
        fail(f"Get Expenses Failed: {res['status']} {res['body']}")

def main():
    try:
        # 1. Setup Data
        unique_suffix = str(uuid.uuid4())[:8]
        email_a = f"alice_{unique_suffix}@test.com"
        email_b = f"bob_{unique_suffix}@test.com"
        pass_a = "password123"
        
        # 2. Register & Login Logic
        auth_a_resp = register_user(f"alice_{unique_suffix}", email_a, pass_a)
        
        if isinstance(auth_a_resp, dict) and ('token' in auth_a_resp or 'accessToken' in auth_a_resp):
            auth_a = auth_a_resp
        else:
            auth_a = login_user(email_a, pass_a)
            
        token_a = auth_a.get('token') or auth_a.get('accessToken')
        id_a = auth_a.get('userId') or auth_a.get('id')
        
        # DEBUG LOGGING
        log(f"DEBUG Auth A: {auth_a}")
        log(f"DEBUG ID A: {id_a}")
        if not id_a:
            fail("User ID A is missing!")

        auth_b_resp = register_user(f"bob_{unique_suffix}", email_b, pass_a)
        
        if isinstance(auth_b_resp, dict) and ('token' in auth_b_resp or 'accessToken' in auth_b_resp):
            auth_b = auth_b_resp
        else:
            auth_b = login_user(email_b, pass_a)

        id_b = auth_b.get('userId') or auth_b.get('id')
        log(f"DEBUG ID B: {id_b}")
        
        # 3. Create Group
        # Passing IDs, not emails
        group = create_group(token_a, f"Test Group {unique_suffix}", [id_a, id_b])
        group_id = group['id']

        # 4. Add Normal Expense (EQUAL)
        add_expense(token_a, group_id, "Lunch", 100.0, id_a, "EQUAL")
        
        # 5. Add Expense (EXACT Split)
        splits_exact = {str(id_a): 10.0, str(id_b): 40.0} # Keys as strings for JSON
        add_expense(token_a, group_id, "Taxi", 50.0, id_a, "EXACT", splits_exact)

        # 6. Verify Expenses
        expenses = get_group_expenses(token_a, group_id)
        log(f"Retrieved {len(expenses)} expenses.")
        if len(expenses) < 2:
            fail("Expected at least 2 expenses.")
        
        # 7. Edge Cases
        log("\n--- Testing Edge Cases ---")
        
        # Edge 1: Zero Amount Expense
        res_zero = add_expense(token_a, group_id, "Zero Cost", 0.0, id_a, "EQUAL")
        if isinstance(res_zero, dict) and 'status' in res_zero and res_zero['status'] == 400:
             log("✅ Correctly rejected zero amount.")
        elif isinstance(res_zero, dict) and 'status' in res_zero:
             log(f"⚠️ Warning: Zero amount accepted (Status {res_zero['status']}).")
        
        # Edge 2: Negative Amount (Using simple request because add_expense wrapper returns body on success)
        desc_encoded = urllib.parse.quote("Negative Cost")
        res_neg = request("POST", f"expenses?userId={id_a}&groupId={group_id}&description={desc_encoded}&amount=-10.0&type=EQUAL", token=token_a)
        if res_neg['status'] == 400:
             log("✅ Correctly rejected negative amount.")
        else:
             log(f"⚠️ Warning: Negative amount accepted (Status {res_neg['status']}).")
             
        log("\n--- Integration Test Complete ---")

    except Exception as e:
        fail(f"Script Crash: {e}")

if __name__ == "__main__":
    main()
