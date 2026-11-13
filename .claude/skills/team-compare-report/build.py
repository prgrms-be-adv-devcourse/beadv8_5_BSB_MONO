"""data.json을 검사하고 template.html에 넣어 리포트 HTML을 만든다.

사용: python3 build.py <data.json> <출력.html>
형식은 data-schema.md. 형식이 틀리면 무엇이 틀렸는지 출력하고 1로 끝난다.
분량·문체 규칙(SKILL.md 「글 쓰는 법」)에 걸리면 경고를 출력하고 HTML은 만든다.
"""
import json
import pathlib
import re
import sys

GRADES = {"good", "ok", "weak", "none", "unknown"}
BADGES = {"s", "m", "l", "warn"}

# SKILL.md 「글 쓰는 법」의 표와 같은 목록이다. 하나를 고치면 둘 다 고친다
STYLE = [
    (r"짚", "확인한다"), (r"끌어내|이끌어 내", "만든다·얻는다"), (r"겪", "~가 불편하다·~가 생긴다"),
    (r"를 통해|을 통해", "~로"), (r"에 대해|에 대한", "~를"), (r"것이 (중요|필요)", "~해야 합니다"),
    (r"효과적|다양한|핵심적", "빼거나 숫자·이름으로"), (r"활용", "쓴다"), (r"진행(한|합|하)", "한다"),
    (r"가능(하|해|합)", "~할 수 있다"), (r"존재(한|합|하)", "있다"), (r"—", "줄표 대신 문장을 나눔"),
]
LIMITS = {"summary": 45, "item": 40, "fact": 40, "abcell": 70}


def plain(s):
    return re.sub(r"\*\*|`", "", str(s))


def lint(d):
    warns = []

    def text(where, s, limit=None):
        p = plain(s)
        if limit and len(p) > limit:
            warns.append(f"{where}: {len(p)}자, {limit}자 이내로 덜어냄 「{p}」")
        for pat, fix in STYLE:
            if re.search(pat, p):
                warns.append(f"{where}: 「{re.search(pat, p).group(0)}」 → {fix} 「{p}」")

    for i, s in enumerate(d.get("summary", [])):
        text(f"summary {i + 1}", s, LIMITS["summary"])
    if len(d.get("summary", [])) > 3:
        warns.append("summary: 3줄 이내로 덜어냄")
    text("footer", d.get("footer", ""))
    for t in d.get("teams", []):
        who = t.get("id", "?")
        for k in ("good", "bad", "take"):
            for s in t.get(k, []) or []:
                text(f"{who}.{k}", s, LIMITS["item"])
            if len(t.get(k, []) or []) > 4:
                warns.append(f"{who}.{k}: 4개 이내로 덜어냄")
        for _, v in t.get("facts", []):
            text(f"{who}.facts", v, LIMITS["fact"])
        for c in t.get("checks", []) or []:
            text(f"{who}.checks {plain(c['item'])}", c.get("text", ""))
            if len(re.findall(r"[.다요]\s", plain(c.get("text", "")) + " ")) > 2:
                warns.append(f"{who}.checks {plain(c['item'])}: 두 문장 이내로 덜어냄")
    for s in d.get("apply", {}).get("summary", []):
        text("apply.summary", s, LIMITS["summary"])
    for g in d.get("apply", {}).get("groups", []):
        for a in g["items"]:
            text(f"적용안 {plain(a['title'])}", a["title"])
            text(f"적용안 {plain(a['title'])}.why", a["why"])
            if "→" not in a["why"]:
                warns.append(f"적용안 {plain(a['title'])}.why: 「문제 → 고치면 어떻게 되는지」 형태로")
    for r in (d.get("strengths") or {}).get("rows", []):
        text(f"장점 {plain(r['title'])}", r["title"], LIMITS["item"])
        for k in ("asis", "tobe"):
            if r.get(k):
                text(f"장점 {plain(r['title'])}.{k}", r[k], LIMITS["abcell"])
    for t in d.get("teams", []):
        for k, v in (t.get("decisions") or {}).items():
            if v.get("why"):
                text(f"{t.get('id')}.decisions {k}", v["why"], LIMITS["abcell"])
    return warns


def check(d):
    errs = []
    for k in ("title", "subtitle", "summary", "footer", "teams", "apply"):
        if not d.get(k):
            errs.append(f"맨 위 `{k}`가 비어 있음")
    ids = set()
    for t in d.get("teams", []):
        who = t.get("id", "?")
        for k in ("id", "no", "name", "product", "facts", "good", "bad"):
            if not t.get(k):
                errs.append(f"팀 {who}: `{k}`가 비어 있음")
        if who in ids:
            errs.append(f"팀 id 중복: {who}")
        ids.add(who)
        for c in t.get("checks", []):
            if c.get("grade") not in GRADES:
                errs.append(f"팀 {who}: 판정 값 `{c.get('grade')}`는 쓸 수 없음")
    if sum(1 for t in d.get("teams", []) if t.get("ours")) != 1:
        errs.append("`ours: true`인 팀이 정확히 하나여야 함")
    for s in (d.get("matrix") or {}).get("sections", []):
        for tid in s["teams"]:
            if tid not in ids:
                errs.append(f"판정표 「{s['title']}」: 없는 팀 id {tid}")
        for r in s["rows"]:
            if len(r["grades"]) != len(s["teams"]):
                errs.append(f"판정표 「{s['title']}」 {r['item']}: 판정 {len(r['grades'])}개, 팀 {len(s['teams'])}개")
            errs += [f"판정표 {r['item']}: 판정 값 `{g}`는 쓸 수 없음" for g in r["grades"] if g not in GRADES]
    for g in d.get("apply", {}).get("groups", []):
        for a in g["items"]:
            if a.get("badge") and a["badge"].get("level") not in BADGES:
                errs.append(f"적용안 {a['title']}: badge.level `{a['badge'].get('level')}`는 쓸 수 없음")
    for r in (d.get("strengths") or {}).get("rows", []):
        who = r.get("title", "?")
        for k in ("title", "ref", "asis"):
            if not r.get(k):
                errs.append(f"장점 {who}: `{k}`가 비어 있음")
        if r.get("status") not in ("todo", "have"):
            errs.append(f"장점 {who}: status는 todo·have만 쓸 수 있음")
        if r.get("status") == "todo" and not r.get("tobe"):
            errs.append(f"장점 {who}: 가져올 것은 `tobe`가 있어야 함")
        if r.get("badge") and r["badge"].get("level") not in BADGES:
            errs.append(f"장점 {who}: badge.level `{r['badge'].get('level')}`는 쓸 수 없음")
    return errs + check_rubric(d) + check_decisions(d)


def check_decisions(d):
    """설계 선택(decisions)이 있을 때만 검사한다. 이유에는 출처가 있어야 한다."""
    D = d.get("decisions")
    if not D:
        return []
    errs = []
    ids = [tp.get("id") for tp in D.get("topics") or []]
    if not ids or len(set(ids)) != len(ids) or not all(tp.get("name") for tp in D["topics"]):
        errs.append("decisions.topics: id가 겹치지 않고 name이 있어야 함")
    for t in d.get("teams", []):
        for k, v in (t.get("decisions") or {}).items():
            where = f"팀 {t.get('id')} 선택 {k}"
            if k not in ids:
                errs.append(f"{where}: topics에 없는 id")
            if not v.get("choice"):
                errs.append(f"{where}: `choice`가 있어야 함")
            if v.get("why") and not v.get("src"):
                errs.append(f"{where}: 이유에는 출처 `src`(문서·PR·커밋·주석)가 있어야 함")
    return errs


def check_rubric(d):
    """종합 점수 리포트(rubric이 있을 때)만 검사한다. 근거 없는 충족은 받지 않는다."""
    r = d.get("rubric")
    if not r:
        return []
    errs = []
    axes = r.get("axes") or []
    ids = [a.get("id") for a in axes]
    if len(set(ids)) != len(ids):
        errs.append("rubric.axes: id 중복")
    rids = [x.get("id") for x in r.get("rulings") or []]
    if len(set(rids)) != len(rids) or not all(x.get("rule") for x in r.get("rulings") or []):
        errs.append("rubric.rulings: id가 겹치지 않고 rule이 있어야 함")
    if sum(a.get("weight", 0) for a in axes) != 100:
        errs.append(f"rubric.axes: 가중치 합이 {sum(a.get('weight', 0) for a in axes)}, 100이어야 함")
    for a in axes:
        if not a.get("name") or len(a.get("items") or []) != 4:
            errs.append(f"축 {a.get('id')}: name과 확인 항목 4개가 있어야 함")
    for t in d.get("teams", []):
        sc = t.get("scores") or {}
        for a in axes:
            s = sc.get(a["id"])
            where = f"팀 {t.get('id')} 축 {a['id']}"
            if s is None:
                errs.append(f"{where}: 점수가 없음. 찾지 못했으면 ok:false로 적음")
            elif s.get("na"):
                if not s.get("why"):
                    errs.append(f"{where}: 해당 없음이면 `why`가 있어야 함")
            elif len(s.get("items") or []) != 4:
                errs.append(f"{where}: items가 4개여야 함")
            else:
                for k, it in enumerate(s["items"]):
                    if not isinstance(it.get("ok"), bool):
                        errs.append(f"{where} 항목 {k + 1}: ok는 true·false")
                    elif it.get("rule") and it["rule"] not in rids:
                        errs.append(f"{where} 항목 {k + 1}: 판정 사례 {it['rule']}가 rulings에 없음")
                    elif not it["ok"] and it.get("why") and it["why"] not in ("반례", "없음", "미확인"):
                        errs.append(f"{where} 항목 {k + 1}: why는 반례·없음·미확인만 쓸 수 있음")
                    elif it.get("via") and (it["via"] != "변호" or not it["ok"]):
                        errs.append(f"{where} 항목 {k + 1}: via는 변호로 뒤집혀 ok가 true일 때만 씀")
                    elif it["ok"] and it.get("defect"):
                        errs.append(f"{where} 항목 {k + 1}: 결함이 확인되면 ok는 false")
                    elif it["ok"] and not it.get("ev"):
                        errs.append(f"{where} 항목 {k + 1}: 충족이면 근거 `ev`(파일:줄)가 있어야 함")
    return errs


def build(data_path, out_path):
    d = json.loads(pathlib.Path(data_path).read_text(encoding="utf-8"))
    errs = check(d)
    if errs:
        print("\n".join(errs))
        sys.exit(1)
    warns = lint(d)
    for w in warns:
        print(f"경고 · {w}")
    # rubric이 있으면 종합 점수 양식, 없으면 기존 양식
    name = "template-score.html" if d.get("rubric") else "template.html"
    tpl = (pathlib.Path(__file__).parent / name).read_text(encoding="utf-8")
    # 데이터 안의 </script>가 스크립트를 닫지 않게 막는다
    payload = json.dumps(d, ensure_ascii=False).replace("</", "<\\/")
    title = d["title"].replace("&", "&amp;").replace("<", "&lt;")
    html = tpl.replace("__TITLE__", title).replace("/*DATA*/null", payload)
    pathlib.Path(out_path).write_text(html, encoding="utf-8")
    print(f"만듦: {out_path} · {name}" +(f" (경고 {len(warns)}개, 고친 뒤 다시 돌림)" if warns else ""))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    build(sys.argv[1], sys.argv[2])
