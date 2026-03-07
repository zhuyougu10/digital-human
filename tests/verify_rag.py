#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""RAG retrieval quality verification script."""
import json
import requests

BASE_KNOWLEDGE = "http://localhost:8085"
BASE_USER = "http://localhost:8080/api"
TIMEOUT = 30

SESSION = requests.Session()

def login():
    r = SESSION.post(f"{BASE_USER}/user/auth/login",
                     json={"username": "admin", "password": "admin123"},
                     timeout=TIMEOUT)
    return r.json()["data"]["token"]

def search(token, kb_id, query, top_k=5):
    r = SESSION.post(f"{BASE_KNOWLEDGE}/kb/search",
                     json={"kbId": kb_id, "query": query, "topK": top_k},
                     headers={"Authorization": f"Bearer {token}"},
                     timeout=TIMEOUT)
    return r.json()

# (kb_id, dept, query, expected_keyword)
CASES = [
    (2,  "内科",    "高血压如何控制血压",      "高血压"),
    (2,  "内科",    "糖尿病胰岛素抵抗",        "糖尿病"),
    (2,  "内科",    "心力衰竭水肿治疗",        "心力衰竭"),
    (3,  "外科",    "阑尾炎手术指征",          "阑尾"),
    (3,  "外科",    "胆囊结石微创手术",        "胆石症"),
    (4,  "神经内科","脑梗死溶栓时间窗",        "脑梗"),
    (4,  "神经内科","帕金森病运动障碍",        "帕金森"),
    (5,  "儿科",    "新生儿黄疸蓝光照射",      "黄疸"),
    (5,  "儿科",    "儿童哮喘雾化治疗",        "哮喘"),
    (6,  "妇产科",  "妊娠期高血压子痫",        "妊娠高血压"),
    (6,  "妇产科",  "多囊卵巢月经不调",        "多囊卵巢"),
    (7,  "眼科",    "白内障超声乳化手术",      "白内障"),
    (7,  "眼科",    "青光眼眼压升高",          "青光眼"),
    (8,  "耳鼻喉科","过敏性鼻炎脱敏治疗",      "过敏性鼻炎"),
    (8,  "耳鼻喉科","突发性耳聋激素治疗",      "突发性耳聋"),
    (9,  "皮肤科",  "湿疹激素药膏使用",        "湿疹"),
    (9,  "皮肤科",  "银屑病免疫调节治疗",      "银屑病"),
    (10, "中医科",  "针灸穴位选穴原则",        "针灸"),
    (10, "中医科",  "气虚体质中药调理方案",    "气虚"),
    (11, "口腔科",  "牙周炎牙龈出血处理",      "牙周"),
]

def main():
    print("Login...", end=" ", flush=True)
    token = login()
    print(f"OK ({token[:8]}...)\n")

    fmt = "{:<8} {:<22} {:>6}  {:^5}  {}"
    print(fmt.format("Dept", "Query", "Score", "Hit", "Top1 preview"))
    print("-" * 80)

    hit = 0
    scores = []
    errors = []

    for kb_id, dept, query, expect in CASES:
        try:
            d = search(token, kb_id, query, top_k=3)
        except Exception as e:
            errors.append((dept, query, str(e)))
            print(fmt.format(dept, query[:22], "ERR", "N/A", str(e)[:40]))
            continue

        if d.get("code") != 200 or not d.get("data"):
            errors.append((dept, query, d.get("msg")))
            print(fmt.format(dept, query[:22], "N/A", "N/A", f"API error: {d.get('msg')}"))
            continue

        top1 = d["data"][0]
        content = top1.get("content", "")
        score = top1.get("score", 0.0)
        scores.append(score)
        matched = expect in content
        if matched:
            hit += 1
        mark = "YES" if matched else "NO"
        preview = content.replace("\n", " ")[:45]
        print(fmt.format(dept, query[:22], f"{score:.4f}", mark, preview))

    n = len(CASES)
    avg = sum(scores) / len(scores) if scores else 0
    print(f"\n{'='*80}")
    print(f"命中率:   {hit}/{n} = {hit/n*100:.1f}%")
    if scores:
        print(f"平均分:   {avg:.4f}")
        print(f"最高分:   {max(scores):.4f}")
        print(f"最低分:   {min(scores):.4f}")
    if errors:
        print(f"\n错误数:   {len(errors)}")
        for dept, q, msg in errors:
            print(f"  [{dept}] {q}: {msg}")

if __name__ == "__main__":
    main()
