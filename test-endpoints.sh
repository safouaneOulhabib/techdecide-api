#!/usr/bin/env bash
# test-endpoints.sh — TechDecide API integration test suite
# Prerequisites:
#   - Server running: cd techdecide-api && mvn spring-boot:run -Dspring.profiles.active=dev
#   - Test data loaded: psql -U <user> -d <db> -f test-data.sql
# Usage: bash test-endpoints.sh

BASE_URL="http://localhost:8080/api"
PASS=0
FAIL=0

# ── Helpers provided by spec ──────────────────────────────────────────────────

login() {
  curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" \
    | grep -o '"token":"[^"]*"' \
    | cut -d'"' -f4
}

assert() {
  local description=$1
  local expected=$2
  local actual=$3
  if [ "$actual" == "$expected" ]; then
    echo "  ✅ PASS: $description (got $actual)"
    PASS=$((PASS + 1))
  else
    echo "  ❌ FAIL: $description (expected $expected, got $actual)"
    FAIL=$((FAIL + 1))
  fi
}

status() {
  curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $1" "$2"
}

# ── Extended helpers ──────────────────────────────────────────────────────────

# req METHOD TOKEN URL [BODY]
# Sets STATUS (http code) and RESPONSE (body)
req() {
  local method="$1" token="$2" url="$3" body="${4:-}"
  local full
  if [ -n "$body" ]; then
    full=$(curl -s -w "%{http_code}" \
      -X "$method" \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      -d "$body" \
      "$url")
  else
    full=$(curl -s -w "%{http_code}" \
      -X "$method" \
      -H "Authorization: Bearer $token" \
      "$url")
  fi
  STATUS="${full: -3}"
  RESPONSE="${full:0:${#full}-3}"
}

# req_noauth METHOD URL [BODY]
req_noauth() {
  local method="$1" url="$2" body="${3:-}"
  local full
  if [ -n "$body" ]; then
    full=$(curl -s -w "%{http_code}" \
      -X "$method" \
      -H "Content-Type: application/json" \
      -d "$body" \
      "$url")
  else
    full=$(curl -s -w "%{http_code}" -X "$method" "$url")
  fi
  STATUS="${full: -3}"
  RESPONSE="${full:0:${#full}-3}"
}

extract_id() {
  echo "$1" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*$'
}

# ── Sanity check ──────────────────────────────────────────────────────────────

PING=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST -H "Content-Type: application/json" -d '{}' \
  "$BASE_URL/auth/login" 2>/dev/null)
if [ "$PING" = "000" ]; then
  echo "ERROR: Cannot reach $BASE_URL — is the server running on port 8080?"
  exit 1
fi

# ── Login all test users ──────────────────────────────────────────────────────

echo ""
echo "=== LOGIN ==="
echo "  Users:  admin | teamadmin.backend | member.backend"
echo "          teamadmin.devops | member.devops | noteam"

TOKEN_ADMIN=$(login "admin@techdecide.com" "Test1234!")
TOKEN_TA1=$(login "teamadmin.backend@techdecide.com" "Test1234!")
TOKEN_M1=$(login "member.backend@techdecide.com" "Test1234!")
TOKEN_TA2=$(login "teamadmin.devops@techdecide.com" "Test1234!")
TOKEN_M2=$(login "member.devops@techdecide.com" "Test1234!")
TOKEN_NOTEAM=$(login "noteam@techdecide.com" "Test1234!")

LOGIN_OK=1
for entry in "APP_ADMIN:$TOKEN_ADMIN" "TEAM_ADMIN1:$TOKEN_TA1" "MEMBER1:$TOKEN_M1" \
             "TEAM_ADMIN2:$TOKEN_TA2" "MEMBER2:$TOKEN_M2" "NO_TEAM:$TOKEN_NOTEAM"; do
  name="${entry%%:*}"; token="${entry#*:}"
  if [ -n "$token" ]; then
    echo "  ✅ $name login OK"
    PASS=$((PASS + 1))
  else
    echo "  ❌ $name login FAILED — run test-data.sql first"
    FAIL=$((FAIL + 1))
    LOGIN_OK=0
  fi
done

if [ "$LOGIN_OK" = "0" ]; then
  echo ""
  echo "❌ One or more logins failed. Ensure test-data.sql is loaded."
  exit 1
fi

# ── AUTH ──────────────────────────────────────────────────────────────────────

echo ""
echo "=== AUTH ==="

TEMP_EMAIL="testscript$$@techdecide.com"

req_noauth POST "$BASE_URL/auth/register" \
  "{\"name\":\"Script User\",\"email\":\"$TEMP_EMAIL\",\"password\":\"Test1234!\"}"
assert "POST /auth/register — new user" "200" "$STATUS"

req_noauth POST "$BASE_URL/auth/login" \
  "{\"email\":\"$TEMP_EMAIL\",\"password\":\"Test1234!\"}"
assert "POST /auth/login — valid credentials" "200" "$STATUS"

req_noauth POST "$BASE_URL/auth/login" \
  "{\"email\":\"$TEMP_EMAIL\",\"password\":\"WrongPass99!\"}"
assert "POST /auth/login — wrong password" "401" "$STATUS"

# ── DECISIONS — setup ─────────────────────────────────────────────────────────

echo ""
echo "=== DECISIONS ==="

D1_BODY='{"title":"[SCRIPT] Decision 1","context":"ctx","decision":"dec","consequences":"cons","teamId":1}'
D2_BODY='{"title":"[SCRIPT] Decision 2","context":"ctx","decision":"dec","consequences":"cons","teamId":1}'
D3_BODY='{"title":"[SCRIPT] Decision 3","context":"ctx","decision":"dec","consequences":"cons","teamId":1}'

req POST "$TOKEN_M1" "$BASE_URL/decisions" "$D1_BODY"
assert "POST /decisions as MEMBER1 — 201 (creates in own team)" "201" "$STATUS"
D1_ID=$(extract_id "$RESPONSE")

req POST "$TOKEN_TA1" "$BASE_URL/decisions" "$D2_BODY"
D2_ID=$(extract_id "$RESPONSE")

req POST "$TOKEN_TA1" "$BASE_URL/decisions" "$D3_BODY"
D3_ID=$(extract_id "$RESPONSE")

if [ -z "$D1_ID" ] || [ -z "$D2_ID" ] || [ -z "$D3_ID" ]; then
  echo "  ⚠️  Decision setup failed (D1=$D1_ID D2=$D2_ID D3=$D3_ID)"
  echo "  ⚠️  Decision-dependent tests may be skipped or fail"
fi

# GET all
req GET "$TOKEN_M1" "$BASE_URL/decisions"
assert "GET /decisions as MEMBER1 — 200" "200" "$STATUS"

req GET "$TOKEN_NOTEAM" "$BASE_URL/decisions"
assert "GET /decisions as NO_TEAM — 200 (empty list)" "200" "$STATUS"

# POST edge cases
req POST "$TOKEN_NOTEAM" "$BASE_URL/decisions" "$D1_BODY"
assert "POST /decisions as NO_TEAM — 400" "400" "$STATUS"

# Wrong teamId: MEMBER1 sends teamId=2, service forces own team (1)
WRONG_TEAM_BODY='{"title":"[SCRIPT] Wrong Team","context":"ctx","decision":"dec","consequences":"cons","teamId":2}'
req POST "$TOKEN_M1" "$BASE_URL/decisions" "$WRONG_TEAM_BODY"
assert "POST /decisions with wrong teamId as MEMBER1 — 201 (own team forced)" "201" "$STATUS"
DWRONG_ID=$(extract_id "$RESPONSE")
[ -n "$DWRONG_ID" ] && req DELETE "$TOKEN_M1" "$BASE_URL/decisions/$DWRONG_ID" || true

# GET by ID
if [ -n "$D1_ID" ]; then
  req GET "$TOKEN_M1" "$BASE_URL/decisions/$D1_ID"
  assert "GET /decisions/$D1_ID as MEMBER1 (own team) — 200" "200" "$STATUS"

  req GET "$TOKEN_M2" "$BASE_URL/decisions/$D1_ID"
  assert "GET /decisions/$D1_ID as MEMBER2 (wrong team) — 403" "403" "$STATUS"

  req GET "$TOKEN_ADMIN" "$BASE_URL/decisions/$D1_ID"
  assert "GET /decisions/$D1_ID as APP_ADMIN — 200" "200" "$STATUS"
fi

# Status transitions on D2
if [ -n "$D2_ID" ]; then
  req PATCH "$TOKEN_M1" "$BASE_URL/decisions/$D2_ID/status" '{"status":"PROPOSED"}'
  assert "PATCH /decisions/$D2_ID/status DRAFT→PROPOSED as MEMBER1 — 200" "200" "$STATUS"

  req PATCH "$TOKEN_M1" "$BASE_URL/decisions/$D2_ID/status" '{"status":"APPROVED"}'
  assert "PATCH /decisions/$D2_ID/status PROPOSED→APPROVED as MEMBER1 — 403" "403" "$STATUS"

  req PATCH "$TOKEN_TA2" "$BASE_URL/decisions/$D2_ID/status" '{"status":"APPROVED"}'
  assert "PATCH /decisions/$D2_ID/status PROPOSED→APPROVED as TEAM_ADMIN2 (wrong team) — 403" "403" "$STATUS"

  req PATCH "$TOKEN_TA1" "$BASE_URL/decisions/$D2_ID/status" '{"status":"APPROVED"}'
  assert "PATCH /decisions/$D2_ID/status PROPOSED→APPROVED as TEAM_ADMIN1 — 200" "200" "$STATUS"
fi

# Status transition on D3 — ADMIN approve
if [ -n "$D3_ID" ]; then
  req PATCH "$TOKEN_TA1" "$BASE_URL/decisions/$D3_ID/status" '{"status":"PROPOSED"}'
  # (setup: no assertion)

  req PATCH "$TOKEN_ADMIN" "$BASE_URL/decisions/$D3_ID/status" '{"status":"APPROVED"}'
  assert "PATCH /decisions/$D3_ID/status PROPOSED→APPROVED as APP_ADMIN — 200" "200" "$STATUS"
fi

# Edit (D1 is still DRAFT)
if [ -n "$D1_ID" ]; then
  UPDATE_BODY='{"title":"[SCRIPT] Updated","context":"ctx","decision":"dec"}'

  req PUT "$TOKEN_M1" "$BASE_URL/decisions/$D1_ID" "$UPDATE_BODY"
  assert "PUT /decisions/$D1_ID as MEMBER1 (own decision, DRAFT) — 200" "200" "$STATUS"

  req PUT "$TOKEN_M2" "$BASE_URL/decisions/$D1_ID" "$UPDATE_BODY"
  assert "PUT /decisions/$D1_ID as MEMBER2 (wrong team) — 403" "403" "$STATUS"

  req DELETE "$TOKEN_M2" "$BASE_URL/decisions/$D1_ID"
  assert "DELETE /decisions/$D1_ID as MEMBER2 (wrong team) — 403" "403" "$STATUS"

  req DELETE "$TOKEN_M1" "$BASE_URL/decisions/$D1_ID"
  assert "DELETE /decisions/$D1_ID as MEMBER1 (own decision, DRAFT) — 204" "204" "$STATUS"
fi

# ── COMMENTS ─────────────────────────────────────────────────────────────────

echo ""
echo "=== COMMENTS ==="

if [ -n "$D2_ID" ]; then
  COMMENT_BODY='{"content":"Script test comment","vote":"APPROVE"}'

  req POST "$TOKEN_M1" "$BASE_URL/decisions/$D2_ID/comments" "$COMMENT_BODY"
  assert "POST /decisions/$D2_ID/comments as MEMBER1 (own team) — 201" "201" "$STATUS"
  C1_ID=$(extract_id "$RESPONSE")

  req POST "$TOKEN_M2" "$BASE_URL/decisions/$D2_ID/comments" "$COMMENT_BODY"
  assert "POST /decisions/$D2_ID/comments as MEMBER2 (wrong team) — 403" "403" "$STATUS"
  # NOTE: CommentService has no team-scope check — this test documents a missing gate

  if [ -n "$C1_ID" ]; then
    req DELETE "$TOKEN_TA1" "$BASE_URL/comments/$C1_ID"
    assert "DELETE /comments/$C1_ID as non-author (TEAM_ADMIN1) — 403" "403" "$STATUS"

    req DELETE "$TOKEN_M1" "$BASE_URL/comments/$C1_ID"
    assert "DELETE /comments/$C1_ID as author (MEMBER1) — 204" "204" "$STATUS"
  else
    echo "  ⚠️  C1 creation failed — skipping comment delete tests"
  fi
else
  echo "  ⚠️  No D2_ID — skipping comment tests"
fi

# ── TEAMS ─────────────────────────────────────────────────────────────────────

echo ""
echo "=== TEAMS ==="

req GET "$TOKEN_ADMIN" "$BASE_URL/teams"
assert "GET /teams as APP_ADMIN — 200 (all teams)" "200" "$STATUS"

req GET "$TOKEN_TA1" "$BASE_URL/teams"
assert "GET /teams as TEAM_ADMIN1 — 200 (own team only)" "200" "$STATUS"

req GET "$TOKEN_NOTEAM" "$BASE_URL/teams"
assert "GET /teams as NO_TEAM — 200 (empty list)" "200" "$STATUS"

# GET members — team 1 (Backend Team, id=1)
req GET "$TOKEN_TA1" "$BASE_URL/teams/1/members"
assert "GET /teams/1/members as TEAM_ADMIN1 (own team) — 200" "200" "$STATUS"

req GET "$TOKEN_M1" "$BASE_URL/teams/1/members"
assert "GET /teams/1/members as MEMBER1 (own team) — 200" "200" "$STATUS"

req GET "$TOKEN_TA2" "$BASE_URL/teams/1/members"
assert "GET /teams/1/members as TEAM_ADMIN2 (wrong team) — 403" "403" "$STATUS"

# Assign NO_TEAM user (id=6) to Backend Team (id=1)
# Authorization failures first (user 6 not yet in team)
ASSIGN_BODY='{"userId":6}'

req POST "$TOKEN_M1" "$BASE_URL/teams/1/members" "$ASSIGN_BODY"
assert "POST /teams/1/members as MEMBER1 — 403" "403" "$STATUS"

req POST "$TOKEN_TA2" "$BASE_URL/teams/1/members" "$ASSIGN_BODY"
assert "POST /teams/1/members as TEAM_ADMIN2 (wrong team) — 403" "403" "$STATUS"

req POST "$TOKEN_TA1" "$BASE_URL/teams/1/members" "$ASSIGN_BODY"
assert "POST /teams/1/members as TEAM_ADMIN1 — 201 (user 6 assigned)" "201" "$STATUS"

# Change role of user 6 in team 1 (to MEMBER to avoid TEAM_ADMIN conflict with user 2)
ROLE_BODY='{"role":"MEMBER"}'

req PATCH "$TOKEN_M1" "$BASE_URL/teams/1/members/6" "$ROLE_BODY"
assert "PATCH /teams/1/members/6 as MEMBER1 — 403" "403" "$STATUS"

req PATCH "$TOKEN_TA1" "$BASE_URL/teams/1/members/6" "$ROLE_BODY"
assert "PATCH /teams/1/members/6 as TEAM_ADMIN1 — 200" "200" "$STATUS"

req PATCH "$TOKEN_ADMIN" "$BASE_URL/teams/1/members/6" "$ROLE_BODY"
assert "PATCH /teams/1/members/6 as APP_ADMIN — 200" "200" "$STATUS"

# Remove user 6 from team 1
req DELETE "$TOKEN_M1" "$BASE_URL/teams/1/members/6"
assert "DELETE /teams/1/members/6 as MEMBER1 — 403" "403" "$STATUS"

req DELETE "$TOKEN_TA1" "$BASE_URL/teams/1/members/6"
assert "DELETE /teams/1/members/6 as TEAM_ADMIN1 — 204 (user 6 removed)" "204" "$STATUS"

# ── REPORTS ───────────────────────────────────────────────────────────────────

echo ""
echo "=== REPORTS ==="

if [ -n "$D2_ID" ]; then
  REPORT_BODY="{\"title\":\"[SCRIPT] Test Report\",\"introduction\":\"intro\",\"decisionIds\":[$D2_ID]}"

  req POST "$TOKEN_NOTEAM" "$BASE_URL/reports" "$REPORT_BODY"
  assert "POST /reports as NO_TEAM — 400" "400" "$STATUS"

  req POST "$TOKEN_M1" "$BASE_URL/reports" "$REPORT_BODY"
  assert "POST /reports as MEMBER1 — 201" "201" "$STATUS"
  R1_ID=$(extract_id "$RESPONSE")

  req GET "$TOKEN_TA1" "$BASE_URL/reports"
  assert "GET /reports as TEAM_ADMIN1 — 200 (own team reports)" "200" "$STATUS"

  req GET "$TOKEN_NOTEAM" "$BASE_URL/reports"
  assert "GET /reports as NO_TEAM — 200 (empty list)" "200" "$STATUS"

  if [ -n "$R1_ID" ]; then
    req GET "$TOKEN_M1" "$BASE_URL/reports/$R1_ID"
    assert "GET /reports/$R1_ID as MEMBER1 (own team) — 200" "200" "$STATUS"

    req GET "$TOKEN_M2" "$BASE_URL/reports/$R1_ID"
    assert "GET /reports/$R1_ID as MEMBER2 (wrong team) — 403" "403" "$STATUS"

    req GET "$TOKEN_ADMIN" "$BASE_URL/reports/$R1_ID"
    assert "GET /reports/$R1_ID as APP_ADMIN — 200" "200" "$STATUS"

    REPORT_UPDATE='{"title":"[SCRIPT] Updated Report"}'

    req PUT "$TOKEN_TA1" "$BASE_URL/reports/$R1_ID" "$REPORT_UPDATE"
    assert "PUT /reports/$R1_ID as non-author (TEAM_ADMIN1, same team) — 403" "403" "$STATUS"

    req PUT "$TOKEN_M1" "$BASE_URL/reports/$R1_ID" "$REPORT_UPDATE"
    assert "PUT /reports/$R1_ID as author (MEMBER1) — 200" "200" "$STATUS"

    req DELETE "$TOKEN_TA1" "$BASE_URL/reports/$R1_ID"
    assert "DELETE /reports/$R1_ID as non-author (TEAM_ADMIN1) — 403" "403" "$STATUS"

    req DELETE "$TOKEN_M1" "$BASE_URL/reports/$R1_ID"
    assert "DELETE /reports/$R1_ID as author (MEMBER1) — 204" "204" "$STATUS"
  else
    echo "  ⚠️  Report creation failed — skipping report detail tests"
  fi
else
  echo "  ⚠️  No D2_ID available — skipping report tests"
fi

# ── ORGANIZATIONS ─────────────────────────────────────────────────────────────

echo ""
echo "=== ORGANIZATIONS ==="

req GET "$TOKEN_ADMIN" "$BASE_URL/organizations"
assert "GET /organizations as APP_ADMIN — 200" "200" "$STATUS"

req GET "$TOKEN_M1" "$BASE_URL/organizations"
assert "GET /organizations as MEMBER1 — 200" "200" "$STATUS"

ORG_NAME="Script Org $$"
req POST "$TOKEN_ADMIN" "$BASE_URL/organizations" \
  "{\"name\":\"$ORG_NAME\",\"description\":\"Created by test script\"}"
assert "POST /organizations as APP_ADMIN — 201" "201" "$STATUS"
ORG_ID=$(extract_id "$RESPONSE")

req POST "$TOKEN_M1" "$BASE_URL/organizations" \
  "{\"name\":\"${ORG_NAME} Member\",\"description\":\"Should be forbidden\"}"
assert "POST /organizations as MEMBER1 — 403" "403" "$STATUS"
# NOTE: OrganizationService has no role gate — this test documents a missing check

# ── CLEANUP ───────────────────────────────────────────────────────────────────

echo ""
echo "=== CLEANUP ==="

if [ -n "$ORG_ID" ]; then
  req DELETE "$TOKEN_ADMIN" "$BASE_URL/organizations/$ORG_ID"
  echo "  🗑  Deleted test org $ORG_ID (HTTP $STATUS)"
fi

echo "  ℹ️  D2=$D2_ID and D3=$D3_ID are APPROVED — cannot be deleted via API"
echo "  ℹ️  Temp user $TEMP_EMAIL remains in the database"

# ── SUMMARY ───────────────────────────────────────────────────────────────────

echo ""
echo "================================"
echo "Results: $PASS passed, $FAIL failed"
echo "================================"

if [ $FAIL -gt 0 ]; then
  exit 1
fi
