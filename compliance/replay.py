#!/usr/bin/env python3
"""Replay the first HTTP request of a TEAM Engine test and print the response.
Usage: replay.py <results.xml> <test local-name> [base-url-override]"""
import sys, urllib.parse, urllib.request, base64, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
NS = "http://www.occamlab.com/ctl"
for log in root.iter("log"):
    st = log.find("starttest")
    if st is None or st.get("local-name") != sys.argv[2]:
        continue
    end = log.find("endtest")
    if end is None or end.get("result") != "6":
        continue
    req = log.find(".//{%s}request" % NS)
    if req is None:
        continue
    url = req.findtext("{%s}url" % NS)
    params = [(p.get("name"), urllib.parse.unquote(p.text or "")) for p in req.findall("{%s}param" % NS)]
    full = url + ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
    full = full.replace("http://nginx", sys.argv[3] if len(sys.argv) > 3 else "http://127.0.0.1:18082")
    print("GET", full)
    r = urllib.request.Request(full, headers={"Host": "nginx"})
    try:
        resp = urllib.request.urlopen(r, timeout=60)
        status, ctype, body = resp.status, resp.headers.get("Content-Type"), resp.read()
    except urllib.error.HTTPError as e:
        status, ctype, body = e.code, e.headers.get("Content-Type"), e.read()
    print(status, ctype)
    print(body[:400])
    break
