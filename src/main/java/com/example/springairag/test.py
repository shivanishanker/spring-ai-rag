import requests
import psycopg2
import time

EMBED_URL = "http://localhost:11434/api/embeddings"
MODEL = "nomic-embed-text"
BATCH = 50


def embed(text):
    res = requests.post(EMBED_URL, json={
        "model": MODEL,
        "prompt": text[:1000]
    })
    data = res.json()
    return data.get("embedding")


def to_vec(arr):
    return "[" + ",".join(map(str, arr)) + "]"


conn = psycopg2.connect(
    dbname="ai_db",
    user="postgres",
    password="postgres",
    host="localhost",
    port="5433"
)
cur = conn.cursor()

cur.execute("""
SELECT 
    a.id,
    a.message,
    a.inference_analysis,
    l.incident_category,
    l.risk_assessment,
    l.recommended_action,
    l.next_action
FROM cc_alerts_alert a
LEFT JOIN cc_alerts_alertllmresponse l
ON a.llm_response_id = l.id
WHERE a.embedding_event IS NULL
LIMIT 5000
""")

rows = cur.fetchall()

count = 0

for row in rows:
    (
        id,
        message,
        inference,
        category,
        risk,
        action,
        next_action
    ) = row

    # 🔥 1. EVENT EMBEDDING
    event_text = f"""
    {message}
    {inference}
    Category: {category}
    """

    # 🔥 2. RISK EMBEDDING
    risk_text = f"""
    Risk: {risk}
    Category: {category}
    """

    # 🔥 3. ACTION EMBEDDING
    action_text = f"""
    Action: {action}
    Next: {next_action}
    """

    e1 = embed(event_text)
    e2 = embed(risk_text)
    e3 = embed(action_text)

    if not e1 or not e2 or not e3:
        print("❌ Skip", id)
        continue

    cur.execute("""
    UPDATE cc_alerts_alert
    SET embedding_event = %s,
        embedding_risk = %s,
        embedding_action = %s
    WHERE id = %s
    """, (
        to_vec(e1),
        to_vec(e2),
        to_vec(e3),
        id
    ))

    count += 1

    if count % BATCH == 0:
        conn.commit()
        print(f"✅ {count} updated")

conn.commit()
cur.close()
conn.close()

print("🔥 DONE:", count)