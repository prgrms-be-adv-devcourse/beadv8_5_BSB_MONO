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
LIMITS = {"summary": 45, "item": 40, "fact": 40}


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
    tpl = (pathlib.Path(__file__).parent / "template.html").read_text(encoding="utf-8")
    # 데이터 안의 </script>가 스크립트를 닫지 않게 막는다
    payload = json.dumps(d, ensure_ascii=False).replace("</", "<\\/")
    title = d["title"].replace("&", "&amp;").replace("<", "&lt;")
    html = tpl.replace("__TITLE__", title).replace("/*DATA*/null", payload)
    pathlib.Path(out_path).write_text(html, encoding="utf-8")
    print(f"만듦: {out_path}" + (f" (경고 {len(warns)}개, 고친 뒤 다시 돌림)" if warns else ""))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    build(sys.argv[1], sys.argv[2])
