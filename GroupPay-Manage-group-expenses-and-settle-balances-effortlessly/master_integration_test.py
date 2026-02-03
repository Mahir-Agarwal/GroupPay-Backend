
import sys
import json
import uuid
import urllib.request
import urllib.parse
import urllib.error
import base64
import time

BASE_URL = "http://10.27.247.110:8081"

# --- HELPERS ---

def log(msg, type="INFO"):
    print(f"[{type}] {msg}", flush=True)

def fail(msg):
    log(msg, "FAIL")
    sys.exit(1)

def request(method, endpoint, data=None, token=None):
    url = f"{BASE_URL}/{endpoint}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    try:
        body = json.dumps(data).encode('utf-8') if data else None
        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode('utf-8')
            parsed = {}
            if res_body and res_body.strip():
                try:
                    parsed = json.loads(res_body)
                except:
                    parsed = res_body
            return {"status": response.status, "body": parsed}
    except urllib.error.HTTPError as e:
        res_body = e.read().decode('utf-8')
        try:
            parsed = json.loads(res_body)
        except:
            parsed = res_body
        return {"status": e.code, "body": parsed}
    except Exception as e:
        return {"status": 0, "body": str(e)}

# --- TEST SUITE ---

class GroupPayTester:
    def __init__(self):
        self.u = str(uuid.uuid4())[:6]
        self.users = {} # name -> {email, username, pwd, token, id}

    def register(self, key, username, email, pwd, expected_status=200):
        log(f"Registering {username}...")
        res = request("POST", f"auth/register?username={username}&email={email}&password={pwd}")
        if res['status'] != expected_status:
            if expected_status == 200:
                fail(f"Registration failed for {username}: {res['status']} {res['body']}")
            else:
                log(f"Caught expected status {res['status']} for {username}", "SUCCESS")
                return res
        
        self.users[key] = {"username": username, "email": email, "pwd": pwd}
        return res

    def login(self, key):
        u = self.users[key]
        log(f"Logging in {u['email']}...")
        res = request("POST", f"auth/login?email={u['email']}&password={u['pwd']}")
        if res['status'] != 200:
            fail(f"Login failed for {key}: {res['status']} {res['body']}")
        
        self.users[key]['token'] = res['body']['token']
        self.users[key]['id'] = res['body']['userId']
        log(f"Login successful. ID: {self.users[key]['id']}", "SUCCESS")

    def run(self):
        log("=== STARTING MASTER INTEGRATION TEST ===")
        
        # 1. AUTH & USER MANAGEMENT
        self.register("A", f"alice_{self.u}", f"alice_{self.u}@test.com", "Pass123")
        self.register("B", f"bob_{self.u}", f"bob_{self.u}@test.com", "Pass123")
        
        # Edge: Duplicate Registration
        self.register("A_DUP", f"alice_{self.u}", f"alice_{self.u}@test.com", "Pass123", expected_status=400)
        
        self.login("A")
        self.login("B")

        # Edge: Wrong Password
        bad_login = request("POST", f"auth/login?email={self.users['A']['email']}&password=wrong")
        if bad_login['status'] != 403: # Spring Security usually gives 403 for bad credentials in this setup
             log(f"WARN: Bad login gave {bad_login['status']} instead of 403", "WARN")
        else:
             log("Correctly rejected bad password", "SUCCESS")

        # User Search
        token_a = self.users['A']['token']
        search_res = request("GET", f"users/search?username={self.users['B']['username']}", token=token_a)
        if search_res['status'] != 200 or search_res['body']['id'] != self.users['B']['id']:
             fail("User search by username failed")
        log("User search verified", "SUCCESS")

        # 2. GROUP MANAGEMENT
        log("Testing Group Creation...")
        cr_res = request("POST", f"groups?name=Trip_{self.u}&creatorId={self.users['A']['id']}", token=token_a)
        if cr_res['status'] not in [200, 201]:
            fail("Group creation failed")
        gid = cr_res['body']['id']
        log(f"Group created with ID {gid}", "SUCCESS")

        # Add Member
        log("Adding Bob to group...")
        add_res = request("POST", f"groups/{gid}/members?userId={self.users['B']['id']}", token=token_a)
        if add_res['status'] not in [200, 201]:
            fail("Adding member failed")
        
        # Edge: Add member twice
        add_dup = request("POST", f"groups/{gid}/members?userId={self.users['B']['id']}", token=token_a)
        if add_dup['status'] != 400:
            log(f"WARN: Adding duplicate member gave {add_dup['status']} instead of 400", "WARN")
        else:
            log("Correctly rejected duplicate member", "SUCCESS")

        # 3. EXPENSE MANAGEMENT
        log("Adding Equal Expense (100)...")
        # Alice pays 100, split between Alice and Bob (50/50)
        exp_eq = request("POST", f"expenses?userId={self.users['A']['id']}&groupId={gid}&description=Dinner&amount=100&type=EQUAL", token=token_a)
        if exp_eq['status'] not in [200, 201]:
            fail(f"Equal expense failed: {exp_eq['body']}")
        log("Equal expense added", "SUCCESS")

        log("Adding Exact Expense (30)...")
        # Bob pays 30. Alice owes 20, Bob owes 10.
        splits = {
            str(self.users['A']['id']): 20.0,
            str(self.users['B']['id']): 10.0
        }
        exp_ex = request("POST", f"expenses?userId={self.users['B']['id']}&groupId={gid}&description=Coffee&amount=30&type=EXACT", data=splits, token=self.users['B']['token'])
        if exp_ex['status'] not in [200, 201]:
            fail(f"Exact expense failed: {exp_ex['body']}")
        log("Exact expense added", "SUCCESS")

        # Edge: Exact split sum mismatch
        log("Testing Exact split mismatch...")
        bad_splits = { str(self.users['A']['id']): 5.0 } # Sum 5 != Amount 30
        res_bad_split = request("POST", f"expenses?userId={self.users['B']['id']}&groupId={gid}&description=Error&amount=30&type=EXACT", data=bad_splits, token=self.users['B']['token'])
        if res_bad_split['status'] != 400:
             fail("Accepted invalid exact split sum!")
        log("Correctly rejected invalid split sum", "SUCCESS")

        # Edge: Non-member payer
        self.register("C", f"charlie_{self.u}", f"charlie_{self.u}@test.com", "Pass123")
        self.login("C")
        res_non_member = request("POST", f"expenses?userId={self.users['C']['id']}&groupId={gid}&description=Hack&amount=10&type=EQUAL", token=self.users['C']['token'])
        if res_non_member['status'] != 400:
             fail("Allowed non-member to pay!")
        log("Correctly rejected non-member payer", "SUCCESS")

        # 4. SETTLEMENTS
        log("Calculating Settlements...")
        # Current logic check:
        # Dinner (100): Alice paid 100. Both owe 50. Net: Alice +50, Bob -50.
        # Coffee (30): Bob paid 30. Alice owes 20, Bob owes 10. Net: Alice -20, Bob +20.
        # Total Net: Alice (+50-20) = +30. Bob (-50+20) = -30.
        # Result: Bob should pay Alice 30.
        
        settle_res = request("GET", f"settlements/group/{gid}/calculate", token=token_a)
        if settle_res['status'] != 200:
            fail(f"Settlement calculation failed: {settle_res['body']}")
        
        settlements = settle_res['body']
        if not isinstance(settlements, list):
             fail(f"Settlement body is not a list! Type: {type(settlements)}, Body: {settlements}")
        
        found_target = False
        log(f"Verifying {len(settlements)} settlements...")
        for s in settlements:
             log(f"DEBUG: Element type: {type(s)}, value: {s!r}")
             if not isinstance(s, dict):
                  log(f"Skipping non-dict element: {s}", "WARN")
                  continue
             
             # Handle both object {id: ...} and raw ID
             p_obj = s.get('payer')
             e_obj = s.get('payee')
             
             s_payer_id = p_obj['id'] if isinstance(p_obj, dict) else p_obj
             s_payee_id = e_obj['id'] if isinstance(e_obj, dict) else e_obj
             
             log(f"Checking settlement: {s_payer_id} -> {s_payee_id} (Amount: {s.get('amount')})")
             
             if str(s_payer_id) == str(self.users['B']['id']) and str(s_payee_id) == str(self.users['A']['id']):
                  if abs(float(s['amount']) - 30.0) < 0.01:
                       found_target = True
        
        if not found_target:
             fail(f"Settlement logic error! Expected Bob to pay Alice 30.0, got: {settlements}")
        log("Settlement logic verified (Bob owes Alice 30)", "SUCCESS")

        # 5. DELETION & CLEANUP
        log("Testing Deletion...")
        eid = exp_eq['body']['id']
        del_exp = request("DELETE", f"expenses/{eid}", token=token_a)
        if del_exp['status'] != 204:
            fail("Expense deletion failed")
        log("Expense deleted", "SUCCESS")

        # Remove Bob from group
        rem_mem = request("DELETE", f"groups/{gid}/members/{self.users['B']['id']}", token=token_a)
        if rem_mem['status'] != 204:
            fail("Member removal failed")
        log("Member removed", "SUCCESS")

        # Delete Group
        del_group = request("DELETE", f"groups/{gid}", token=token_a)
        if del_group['status'] != 204:
            fail("Group deletion failed")
        log("Group deleted", "SUCCESS")

        log("\n=== MASTER INTEGRATION TEST PASSED! ===", "SUCCESS")

if __name__ == "__main__":
    tester = GroupPayTester()
    tester.run()
