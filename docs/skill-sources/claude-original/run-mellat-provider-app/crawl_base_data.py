#!/usr/bin/env python3
"""One-time crawl of Bank Mellat's own province/city base data (DBS-1747),
through mellatProviderApp's own /api/base-data/* endpoints (no token needed -
this service has no app-level auth) - not by re-implementing Mellat's login
flow (private-key-JWT client assertion, see MellatAuthTokenService) in a
second, throwaway place.

Usage (normally invoked via smoke.sh's own "crawl" command, not directly):
    python crawl_base_data.py <app_base_url> <output_path>

<app_base_url> already includes the /mellat-provider context-path, e.g.
http://localhost:8088/mellat-provider - see smoke.sh's APP_URL.

Requires the app to already be running against Bank Mellat's REAL sandbox
(feign.mellat.*.base-url), not the fake DO_NOT_CALL test placeholder - a
crawl against the placeholder just produces connection-refused errors for
every province, not real data.

Stdlib-only (urllib), deliberately - this is a one-off tool, not something
worth adding a requests/jq dependency for.

Output shape:
{
  "provinces": [{"provinceId": "10", "provinceName": "..."}, ...],
  "citiesByProvince": {"10": [{"cityId": "759", "cityName": "..."}, ...], ...},
  "failedProvinceIds": ["17", ...]
}
"failedProvinceIds" lists any province whose city lookup errored out (e.g. a
transient Mellat failure) - a subsequent, cheaper re-run can target just
those instead of re-crawling everything.
"""

import json
import sys
import urllib.error
import urllib.request


def fetch(url):
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        # The response body is this service's own RFC7807 problem detail
        # (detail/errorCode/message - see ExceptionTranslator/MellatErrorMapper),
        # far more useful for diagnosing a failed crawl than the bare status code.
        body = e.read().decode("utf-8", errors="replace")
        raise urllib.error.URLError(f"{e.code} {e.reason} - {body}") from e


def main():
    if len(sys.argv) != 3:
        print(f"usage: {sys.argv[0]} <app_base_url> <output_path>", file=sys.stderr)
        return 1

    base_url, output_path = sys.argv[1], sys.argv[2]

    print("Fetching provinces...", file=sys.stderr)
    try:
        provinces = fetch(f"{base_url}/api/base-data/provinces")
    except urllib.error.URLError as e:
        print(f"Failed to fetch provinces: {e}", file=sys.stderr)
        return 1

    print(f"Got {len(provinces)} provinces. Fetching cities for each...", file=sys.stderr)
    cities_by_province = {}
    failed_province_ids = []
    for i, province in enumerate(provinces, start=1):
        province_id = province["provinceId"]
        try:
            cities_by_province[province_id] = fetch(f"{base_url}/api/base-data/provinces/{province_id}/cities")
        except urllib.error.URLError as e:
            print(f"  [{i}/{len(provinces)}] province {province_id} ({province.get('provinceName')}): FAILED - {e}", file=sys.stderr)
            failed_province_ids.append(province_id)
            continue
        print(f"  [{i}/{len(provinces)}] province {province_id} ({province.get('provinceName')}): {len(cities_by_province[province_id])} cities", file=sys.stderr)

    result = {"provinces": provinces, "citiesByProvince": cities_by_province, "failedProvinceIds": failed_province_ids}
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    total_cities = sum(len(v) for v in cities_by_province.values())
    print(f"\nWrote {output_path}: {len(provinces)} provinces, {total_cities} cities across {len(cities_by_province)} provinces.", file=sys.stderr)
    if failed_province_ids:
        print(f"WARNING: {len(failed_province_ids)} province(s) failed and are missing city data: {failed_province_ids}", file=sys.stderr)
        print("Re-run the crawl (or fix connectivity) before treating this as a complete dataset.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
