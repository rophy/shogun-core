#!/usr/bin/env python3
"""Compare two TEAM Engine CTL result logs (direct vs. through SHOGun-Core).

Usage: summarize.py <direct.xml> <shogun.xml> [--details]
       summarize.py <run.xml>   (counts and failures of one run)

Prints result counts for both runs and lists the tests that pass directly but
not through SHOGun-Core, grouped by their first failure message.
"""
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict

# TEAM Engine (CTL) result codes
RESULTS = {-1: "continue", 0: "best practice", 1: "pass", 2: "not tested",
           3: "skipped", 4: "warning", 5: "inherited failure", 6: "fail"}


TESTNG = {"PASS": 1, "SKIP": 3, "FAIL": 6}


def load_testng(root):
    """TestNG results (WFS 2.0 and other TestNG based suites)."""
    tests = {}
    seen = Counter()
    for cls in root.iter("class"):
        for method in cls.findall("test-method"):
            if method.get("is-config") == "true":
                continue
            name = "{}.{}".format(cls.get("name").split(".")[-1], method.get("name"))
            params = " ".join((v.text or "").strip() for v in method.iter("value"))[:150]
            message = (method.findtext("exception/message") or "").strip()
            seen[(name, params)] += 1
            tests[(name, params, seen[(name, params)])] = (
                name, params, TESTNG.get(method.get("status"), 0), [message[:300]] if message else [])
    return tests


def load(path):
    """Return {test key: (name, assertion, result, [messages])}.

    The key is the test name, its assertion and a counter, because the test
    paths change when a run takes other branches.
    """
    root = ET.parse(path).getroot()
    if root.tag == "testng-results":
        return load_testng(root)
    tests = {}
    seen = Counter()
    for log in root.iter("log"):
        start = log.find("starttest")
        end = log.find("endtest")
        if start is None or end is None:
            continue
        name = "{}:{}".format(start.get("prefix"), start.get("local-name"))
        assertion = (start.findtext("assertion") or "").strip()
        messages = [m.text.strip() for m in log.findall("message") if m.text]
        seen[(name, assertion)] += 1
        tests[(name, assertion, seen[(name, assertion)])] = (name, assertion, int(end.get("result")), messages)
    return tests


def counts(tests):
    c = Counter(RESULTS.get(r, str(r)) for _, _, r, _ in tests.values())
    return ", ".join("{} {}".format(v, k) for k, v in c.most_common())


def single(path):
    tests = load(path)
    print("{} : {} tests: {}".format(path, len(tests), counts(tests)))
    for name, assertion, result, messages in tests.values():
        if result == 6:
            print("  FAIL {}: {} | {}".format(name, assertion[:100], " | ".join(messages[:2])[:300]))


def main():
    files = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(files) == 1:
        single(files[0])
        return
    direct, shogun = load(files[0]), load(files[1])
    details = "--details" in sys.argv
    print("direct : {} tests: {}".format(len(direct), counts(direct)))
    print("shogun : {} tests: {}".format(len(shogun), counts(shogun)))

    groups = defaultdict(list)
    for path, (name, assertion, result, messages) in shogun.items():
        if result != 6:  # own failures only, not inherited ones
            continue
        d = direct.get(path)
        if d is not None and d[2] == 6:
            continue  # fails directly as well: not a SHOGun-Core gap
        key = " | ".join(messages[:2]) if messages else "(no message)"
        groups[key].append((name, assertion))

    total = sum(len(v) for v in groups.values())
    print("\nfail only through SHOGun-Core: {} tests in {} groups".format(total, len(groups)))
    for key, items in sorted(groups.items(), key=lambda kv: -len(kv[1])):
        print("\n[{}] {}".format(len(items), key[:300]))
        for name, assertion in items[: (len(items) if details else 3)]:
            print("    - {}: {}".format(name, assertion[:150]))


if __name__ == "__main__":
    main()
