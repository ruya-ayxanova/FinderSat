import csv, json, sys

STATUS = {"VERIFIED_ACTIVE", "UNVERIFIED", "INACTIVE"}
POL = {"H", "V", "L", "R"}
BAND = {"Ku", "C", "Ka"}

def num(v, cast):
    v = v.strip()
    return cast(v) if v else None

def main(csv_path, out_path, source, date, version):
    sats, errors = {}, []
    with open(csv_path, newline="", encoding="utf-8") as f:
        for n, r in enumerate(csv.DictReader(f), start=2):
            sid = r["id"].strip()
            pos = num(r["position"], float)
            status = r["status"].strip() or "UNVERIFIED"
            if status not in STATUS: errors.append(f"satır {n}: geçersiz status")
            if pos is not None and not -180 <= pos <= 180: errors.append(f"satır {n}: geçersiz konum")
            s = sats.setdefault(sid, {
                "id": sid, "name": r["name"].strip(), "operator": r["operator"].strip() or None,
                "position": pos, "status": status,
                "regions": [x for x in r["regions"].split(";") if x.strip()], "transponders": []})
            if (s["name"], s["position"], s["status"]) != (r["name"].strip(), pos, status):
                errors.append(f"satır {n}: {sid} için tutarsız uydu bilgisi")
            if r["freq"].strip():
                pol = r["pol"].strip() or None
                band = r["band"].strip() or None
                if pol and pol not in POL: errors.append(f"satır {n}: geçersiz polarizasyon")
                if band and band not in BAND: errors.append(f"satır {n}: geçersiz bant")
                s["transponders"].append({
                    "freq": num(r["freq"], int), "pol": pol, "sr": num(r["sr"], int),
                    "fec": r["fec"].strip() or None, "band": band})
    if errors:
        sys.exit("Hata:\n" + "\n".join(errors))
    out = {"meta": {"version": int(version), "source": source, "date": date},
           "satellites": list(sats.values())}
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"{len(sats)} uydu yazıldı")

if __name__ == "__main__":
    # python tools/csv_to_catalog.py veri.csv catalog.json "KaynakAdı" 2026-11-12 2
    main(*sys.argv[1:6])
