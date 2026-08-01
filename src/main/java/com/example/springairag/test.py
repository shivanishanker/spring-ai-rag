import requests
import psycopg2

def trim(val, limit=150):
    if val is None:
        return ""
    val = str(val)
    return val[:limit]

conn = psycopg2.connect(
    dbname="ai_db",
    user="postgres",
    password="postgres",
    host="localhost",
    port="5433"
)

cur = conn.cursor()

cur.execute("""
    SELECT id, message, inference_analysis, 
           llm_response_id, severity, detection_class, 
           confidence, alert_analysis, latitude, 
           longitude, start_time, end_time, duration, 
           is_resolved, resolved_at, resolution_notes, 
           is_active, resolved_by_id
    FROM public.cc_alerts_alert
    WHERE embedding IS NULL
""")

rows = cur.fetchall()

for row in rows:
    (
        id, message, inference_analysis, llm_response_id,
        severity, detection_class, confidence, alert_analysis,
        latitude, longitude, start_time, end_time, duration,
        is_resolved, resolved_at, resolution_notes,
        is_active, resolved_by_id
    ) = row

    # 🧠 Compact structured text (ALL columns included)
    text = f"""
    Msg:{trim(message)}
    Inf:{trim(inference_analysis)}
    Alert:{trim(alert_analysis)}
    Sev:{severity}
    Class:{detection_class}
    Conf:{confidence}
    Loc:{latitude},{longitude}
    Start:{start_time}
    End:{end_time}
    Dur:{duration}
    Resolved:{is_resolved}
    ResolvedAt:{resolved_at}
    Notes:{trim(resolution_notes)}
    Active:{is_active}
    By:{resolved_by_id}
    """

    # Final safety limit
    text = text[:1200]

    res = requests.post(
        "http://localhost:11434/api/embeddings",
        json={
            "model": "nomic-embed-text",
            "prompt": text
        }
    )

    data = res.json()

    if "embedding" not in data:
        print("Error:", data)
        continue

    embedding = data["embedding"]
    embedding_str = "[" + ",".join(map(str, embedding)) + "]"

    cur.execute("""
        UPDATE public.cc_alerts_alert
        SET embedding = %s
        WHERE id = %s
    """, (embedding_str, id))

conn.commit()
cur.close()
conn.close()