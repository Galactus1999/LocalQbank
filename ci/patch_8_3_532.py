#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path.cwd()
SRC = ROOT / "app/src/main/java/com/localqbank/library/RovexOnline.kt"
ACT = ROOT / "app/src/main/java/com/localqbank/library/RovexOnlineActivity.kt"
RULES = ROOT / "firestore.rules"
GRADLE = ROOT / "app/build.gradle.kts"

for p in (SRC, ACT, RULES, GRADLE):
    if not p.is_file():
        raise SystemExit(f"ERROR missing {p}")

src0, act0, rules0, gradle0 = SRC.read_text(), ACT.read_text(), RULES.read_text(), GRADLE.read_text()

# Ensure the generated RovexOnline.kt has the Firestore DocumentReference import used by the mailbox materializer.
src1_import = "import com.google.firebase.firestore.DocumentReference"
if gradle0.count("versionCode = 621") != 1 or gradle0.count('versionName = "8.3.531"') != 1:
    raise SystemExit("ERROR: input is not clean 8.3.531/621")
if any("friendCodes" in ln and ".get()" in ln and not ln.lstrip().startswith("//") for ln in act0.splitlines()):
    raise SystemExit("ERROR: executable friendCodes read in Activity")

def function_span(text, name):
    start = text.find("fun " + name)
    if start < 0: raise SystemExit("ERROR function missing " + name)
    brace = text.find("{", start)
    depth = 0; quote = False; esc = False
    for i in range(brace, len(text)):
        c = text[i]
        if quote:
            if esc: esc = False
            elif c == "\\": esc = True
            elif c == '"': quote = False
        else:
            if c == '"': quote = True
            elif c == "{": depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0: return start, i + 1
    raise SystemExit("ERROR unterminated " + name)

sender = '''fun sendFriendRequestByCode(code: String, requester: FirebaseUser): com.google.android.gms.tasks.Task<Void> {
        val normalized = code.trim().uppercase(Locale.US)
        require(RovexFriendRequestState.isValidCode(normalized)) { "Invalid Rovex ID" }
        val requestId = db.collection("friendRequestMailboxes").document(normalized)
            .collection("requests").document().id
        val requesterCode = RovexOnlineAuth.rovexId(requester.uid)
        val data = mapOf(
            "requestId" to requestId,
            "requesterUid" to requester.uid,
            "requesterName" to RovexOnlineAuth.normalizedName(requester.displayName),
            "requesterCode" to requesterCode,
            "targetCode" to normalized,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )
        val mailbox = db.collection("friendRequestMailboxes").document(normalized)
            .collection("requests").document(requestId)
        val outgoing = db.collection("friendRequestsBySender").document(requester.uid)
            .collection("outgoing").document(requestId)
        val batch = db.batch()
        batch.set(mailbox, data)
        batch.set(outgoing, data)
        return batch.commit()
    }'''

materializer = '''fun materializePendingFriendRequests(user: FirebaseUser): com.google.android.gms.tasks.Task<Void> {
        val myCode = RovexOnlineAuth.rovexId(user.uid)
        val mailbox = db.collection("friendRequestMailboxes").document(myCode)
            .collection("requests").limit(50)
        return mailbox.get().continueWithTask { task ->
            if (!task.isSuccessful) {
                throw task.exception ?: IllegalStateException("Could not read friend request mailbox")
            }
            val docs = task.result.documents
            if (docs.isEmpty()) return@continueWithTask com.google.android.gms.tasks.Tasks.forResult(null)
            val batch = db.batch()
            val deletions = mutableListOf<DocumentReference>()
            docs.forEach { requestDoc ->
                val requesterUid = requestDoc.getString("requesterUid")?.trim().orEmpty()
                if (requesterUid.isEmpty() || requesterUid == user.uid) return@forEach
                val requestId = requestDoc.getString("requestId") ?: requestDoc.id
                val request = mapOf(
                    "intentId" to requestId,
                    "requesterUid" to requesterUid,
                    "requesterName" to (requestDoc.getString("requesterName") ?: "Rovex User"),
                    "requesterCode" to (requestDoc.getString("requesterCode") ?: ""),
                    "targetUid" to user.uid,
                    "targetCode" to myCode,
                    "status" to "pending",
                    "createdAt" to (requestDoc.get("createdAt") ?: FieldValue.serverTimestamp())
                )
                val incoming = db.collection("friendRequests").document(user.uid)
                    .collection("incoming").document(requesterUid)
                batch.set(incoming, request, SetOptions.merge())
                deletions += requestDoc.reference
            }
            batch.commit().continueWithTask { committed ->
                if (!committed.isSuccessful) {
                    throw committed.exception ?: IllegalStateException("Could not materialize friend requests")
                }
                val deleteBatch = db.batch()
                deletions.forEach { deleteBatch.delete(it) }
                deleteBatch.commit()
            }
        }
    }'''

def replace_function(text, name, replacement):
    a, b = function_span(text, name)
    return text[:a] + replacement + text[b:]

src1 = replace_function(src0, "sendFriendRequestByCode", sender)
src1 = replace_function(src1, "materializePendingFriendRequests", materializer)

def replace_rule(text, header, replacement):
    pos = text.find(header)
    if pos < 0: raise SystemExit("ERROR rule missing " + header)
    brace = text.find("{", pos)
    depth = 0
    for i in range(brace, len(text)):
        c = text[i]
        if c == "{": depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0: return text[:pos] + replacement.rstrip() + "\n" + text[i+1:]
    raise SystemExit("ERROR unterminated rule " + header)

mailbox = '''match /friendRequestMailboxes/{targetCode}/requests/{requestId} {
      allow create: if signedIn() &&
        request.resource.data.keys().hasOnly([
          'requestId', 'requesterUid', 'requesterName', 'requesterCode',
          'targetCode', 'status', 'createdAt'
        ]) &&
        request.resource.data.requestId == requestId &&
        request.resource.data.requesterUid == request.auth.uid &&
        request.resource.data.targetCode == targetCode &&
        targetCode.matches('RVX-[A-Z0-9]{4}-[A-Z0-9]{4}') &&
        request.resource.data.status == 'pending';
      allow read: if signedIn() &&
        get(/databases/$(database)/documents/friendCodes/$(targetCode)).data.uid == request.auth.uid;
      allow update: if false;
      allow delete: if signedIn() &&
        get(/databases/$(database)/documents/friendCodes/$(targetCode)).data.uid == request.auth.uid;
    }'''

incoming = '''match /friendRequests/{targetUid}/incoming/{requesterUid} {
      allow create: if signedIn() &&
        requesterUid != targetUid &&
        targetUid == request.auth.uid &&
        request.resource.data.targetUid == request.auth.uid &&
        request.resource.data.requesterUid == requesterUid &&
        request.resource.data.targetCode is string &&
        request.resource.data.intentId is string &&
        exists(/databases/$(database)/documents/friendRequestMailboxes/$(request.resource.data.targetCode)/requests/$(request.resource.data.intentId)) &&
        get(/databases/$(database)/documents/friendRequestMailboxes/$(request.resource.data.targetCode)/requests/$(request.resource.data.intentId)).data.requesterUid == requesterUid &&
        get(/databases/$(database)/documents/friendRequestMailboxes/$(request.resource.data.targetCode)/requests/$(request.resource.data.intentId)).data.requestId == request.resource.data.intentId;
      allow get, list: if signedIn() && request.auth.uid == targetUid;
      allow update: if signedIn() &&
        request.auth.uid == targetUid &&
        request.resource.data.diff(resource.data).affectedKeys().hasOnly(['status', 'respondedAt']);
      allow delete: if signedIn() && request.auth.uid == targetUid;
    }'''

outgoing = '''match /friendRequestsBySender/{requesterUid}/outgoing/{requestId} {
      allow create: if signedIn() &&
        requesterUid == request.auth.uid &&
        request.resource.data.keys().hasOnly([
          'requestId', 'requesterUid', 'requesterName', 'requesterCode',
          'targetCode', 'status', 'createdAt'
        ]) &&
        request.resource.data.requestId == requestId &&
        request.resource.data.requesterUid == request.auth.uid &&
        request.resource.data.targetCode is string &&
        request.resource.data.targetCode.matches('RVX-[A-Z0-9]{4}-[A-Z0-9]{4}') &&
        request.resource.data.status == 'pending';
      allow get, list: if signedIn() && request.auth.uid == requesterUid;
      allow update: if signedIn() &&
        get(/databases/$(database)/documents/friendCodes/$(resource.data.targetCode)).data.uid == request.auth.uid &&
        request.resource.data.diff(resource.data).affectedKeys().hasOnly(['status', 'respondedAt', 'targetUid']) &&
        request.resource.data.status in ['accepted', 'declined'];
      allow delete: if signedIn() && request.auth.uid == requesterUid;
    }'''

rules1 = replace_rule(rules0, "match /friendRequestMailboxes/{targetCode}/requests/{requestId}", mailbox)
rules1 = replace_rule(rules1, "match /friendRequests/{targetUid}/incoming/{requesterUid}", incoming)
rules1 = replace_rule(rules1, "match /friendRequestsBySender/{requesterUid}/outgoing/{requestId}", outgoing)

m = re.search(r'match /friendCodes/\{code\} \{[\s\S]*?\n\s*\}', rules1)
if not m: raise SystemExit("ERROR friendCodes rule missing")
friendcodes = '''match /friendCodes/{code} {
    allow read: if false;
    allow create, update: if signedIn() && request.resource.data.uid == request.auth.uid;
    allow delete: if signedIn() && resource.data.uid == request.auth.uid;
  }'''
rules1 = rules1[:m.start()] + friendcodes + rules1[m.end():]
gradle1 = gradle0.replace("versionCode = 621", "versionCode = 622", 1).replace('versionName = "8.3.531"', 'versionName = "8.3.532"', 1)

sender_block = re.search(r"fun sendFriendRequestByCode[\s\S]*?(?=\n\s*fun\s|\Z)", src1).group(0)
mat_block = re.search(r"fun materializePendingFriendRequests[\s\S]*?(?=\n\s*fun\s|\Z)", src1).group(0)
checks = [
    'versionName = "8.3.532"' in gradle1 and "versionCode = 622" in gradle1,
    'collection("friendCodes")' not in sender_block,
    '"targetUid" to' not in sender_block,
    'whereEqualTo("targetUid"' not in mat_block,
    bool(re.search(r"match /friendCodes/\{code\} \{\s*allow read: if false;", rules1)),
    "friendCodes/$(targetCode)" in mailbox,
    rules1.count("{") == rules1.count("}")
]
for i, ok in enumerate(checks, 1):
    if not ok: raise SystemExit(f"ERROR static check {i}")
if "import com.google.firebase.firestore.DocumentReference" not in src1:
    src1 = src1.replace(
        "import com.google.firebase.firestore.FieldValue\n",
        "import com.google.firebase.firestore.FieldValue\nimport com.google.firebase.firestore.DocumentReference\n",
        1
    )
SRC.write_text(src1); RULES.write_text(rules1); GRADLE.write_text(gradle1)
print("PASS v8.3.532 social transport patch")
print("PASS versionCode=622 versionName=8.3.532")
print("PASS sender has no client friendCodes lookup")
print("PASS materializer has no targetUid query")
print("PASS friendCodes client reads denied")
print("PASS mailbox target ownership resolved server-side")
print("PASS rules brace balance")
