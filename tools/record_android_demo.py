#!/usr/bin/env python3
"""Record the installed Stillnote Android app on a fresh, dedicated emulator.

Uses only Python's standard library and adb from PATH. The MP4 is the device's
original screenrecord output; no simulated screens or video compositing are used.
"""

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET


PACKAGE = "com.stillnote.app"
ACTIVITY = PACKAGE + "/.MainActivity"
FIRST = {"title": "Morning ideas", "body": "Keep small thoughts in one place.", "pinned": True}
SECOND = {"title": "Weekend plan", "body": "Walk, read, and enjoy coffee.", "pinned": False}


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="milliseconds")


class Demo:
    def __init__(self, output):
        self.output = output
        self.output.mkdir(parents=True, exist_ok=True)
        self.started = time.monotonic()
        self.serial = self.select_emulator()
        self.events = []
        self.assertions = []
        self.recording = None
        self.record_log = None
        self.record_pid = None
        self.record_started = None
        self.video = self.output / "stillnote-android-demo.mp4"
        self.remote_video = "/sdcard/stillnote-demo-" + str(time.time_ns()) + ".mp4"
        self.remote_xml = "/sdcard/stillnote-demo-ui.xml"
        self.result = {
            "started_at_utc": utc_now(), "device_serial": self.serial,
            "app_package": PACKAGE, "video_is_original_screenrecord": True,
            "events": self.events, "assertions": self.assertions,
        }

    @staticmethod
    def select_emulator():
        result = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True, timeout=15)
        devices = [line.split()[0] for line in result.stdout.splitlines()[1:]
                   if len(line.split()) >= 2 and line.split()[1] == "device"]
        requested = os.environ.get("ANDROID_SERIAL")
        if requested:
            if requested not in devices or not re.fullmatch(r"emulator-\d+", requested):
                raise RuntimeError("ANDROID_SERIAL must identify an online Android emulator")
            return requested
        if len(devices) != 1 or not re.fullmatch(r"emulator-\d+", devices[0]):
            raise RuntimeError("Exactly one online emulator is required, or set ANDROID_SERIAL")
        return devices[0]

    def adb(self, *args, check=True, timeout=20, binary=False):
        result = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True,
                                timeout=timeout)
        if check and result.returncode:
            raise RuntimeError("adb command failed: " + repr(args) + "\n" +
                               result.stderr.decode("utf-8", errors="replace"))
        return result if binary else result.stdout.decode("utf-8", errors="replace")

    def event(self, name, **extra):
        item = {"name": name, "at_utc": utc_now(),
                "elapsed_seconds": round(time.monotonic() - self.started, 3), **extra}
        if self.record_started is not None:
            item["video_elapsed_seconds"] = round(time.monotonic() - self.record_started, 3)
        self.events.append(item)
        print(json.dumps(item, ensure_ascii=False), flush=True)

    def step(self, name, action, pause=2.5):
        if self.record_started is not None and time.monotonic() - self.record_started > 165:
            raise RuntimeError("Demo exceeded the safe recording time budget")
        self.event(name, status="started")
        action()
        time.sleep(pause)
        self.event(name, status="completed")

    def ui(self):
        self.adb("shell", "uiautomator", "dump", self.remote_xml, timeout=20)
        xml = self.adb("shell", "cat", self.remote_xml)
        (self.output / "last-ui.xml").write_text(xml, encoding="utf-8")
        root = ET.fromstring(xml)
        parents = {child: parent for parent in root.iter() for child in parent}
        return root, parents

    @staticmethod
    def bounds(node):
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not match:
            return None
        left, top, right, bottom = map(int, match.groups())
        return (left, top, right, bottom) if right > left and bottom > top else None

    def node(self, label, field=False):
        root, parents = self.ui()
        candidates = []
        for node in root.iter("node"):
            if node.get("package") != PACKAGE or node.get("enabled") == "false":
                continue
            if node.get("text") != label and node.get("content-desc") != label:
                continue
            target = node
            while target is not None:
                if field and target.get("class") == "android.widget.EditText":
                    break
                if not field and target.get("clickable") == "true":
                    break
                target = parents.get(target)
            if target is None:
                continue
            bounds = self.bounds(target)
            if bounds is not None:
                candidates.append(bounds)
        candidates = list(dict.fromkeys(candidates))
        if len(candidates) != 1:
            raise RuntimeError(f"Expected one {'field' if field else 'button'} for {label!r}, found {candidates}")
        return candidates[0]

    def tap(self, label, field=False):
        left, top, right, bottom = self.node(label, field)
        self.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        self.event("tap", label=label)
        time.sleep(0.25)

    def type_text(self, label, text):
        if not re.fullmatch(r"[A-Za-z0-9 ,.]+", text):
            raise ValueError("This demo's adb input path intentionally accepts only its ASCII sample text")
        self.tap(label, field=True)
        self.adb("shell", "input", "text", text.replace(" ", "%s"))
        self.event("text_entered", field=label, value=text)

    def hide_keyboard(self):
        output = self.adb("shell", "dumpsys", "input_method")
        if re.search(r"(?:mInputShown|mIsInputViewShown|isInputViewShown)=true\b", output):
            self.adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.5)

    def texts(self):
        root, _ = self.ui()
        return {node.get("text", "") for node in root.iter("node")
                if node.get("package") == PACKAGE}

    def assert_list(self, count, visible, absent=(), pinned=False):
        texts = self.texts()
        expected = [str(count) + "개", *visible] + (["고정"] if pinned else [])
        missing = [value for value in expected if value not in texts]
        unexpected = [value for value in absent if value in texts]
        if missing or unexpected:
            raise AssertionError(f"List assertion failed: missing={missing}, unexpected={unexpected}, texts={sorted(texts)}")
        self.assertions.append({"name": "visible_notes", "at_utc": utc_now(),
                                "count": count, "titles": list(visible), "pinned_visible": pinned, "passed": True})

    def saved_notes(self, check=True):
        result = self.adb("shell", "run-as", PACKAGE, "cat", "files/stillnote-notes.json",
                          check=False, binary=True)
        if result.returncode:
            if not check and b"No such file" in result.stderr + result.stdout:
                return []
            raise RuntimeError("Could not read app-private demo notes: " +
                               (result.stderr + result.stdout).decode("utf-8", errors="replace"))
        return json.loads(result.stdout.decode("utf-8"))

    def assert_saved(self):
        notes = self.saved_notes()
        actual = sorted(({"title": note["title"], "body": note["body"],
                          "pinned": note.get("pinned", False)} for note in notes),
                        key=lambda note: note["title"])
        expected = sorted([FIRST, SECOND], key=lambda note: note["title"])
        if actual != expected:
            raise AssertionError("Persisted notes mismatch: " + repr(actual))
        self.assertions.append({"name": "persisted_notes", "at_utc": utc_now(),
                                "notes": actual, "passed": True})

    def screenshot(self, name):
        result = self.adb("exec-out", "screencap", "-p", binary=True)
        (self.output / (name + ".png")).write_bytes(result.stdout)
        self.event("screenshot", file=name + ".png")

    def start_recording(self):
        existing = self.adb("shell", "pidof", "screenrecord", check=False).strip()
        if existing:
            raise RuntimeError("A screenrecord process is already running on this emulator")
        self.record_log = (self.output / "screenrecord.log").open("wb")
        self.recording = subprocess.Popen(
            ["adb", "-s", self.serial, "shell", "screenrecord", "--time-limit", "180",
             "--bit-rate", "4000000", self.remote_video],
            stdout=self.record_log, stderr=subprocess.STDOUT,
        )
        self.record_started = time.monotonic()
        self.result["recording_started_at_utc"] = utc_now()
        for _ in range(12):
            pid = self.adb("shell", "pidof", "screenrecord", check=False).strip()
            if re.fullmatch(r"\d+", pid):
                self.record_pid = pid
                self.event("recording_started", device_pid=pid)
                return
            if self.recording.poll() is not None:
                break
            time.sleep(0.25)
        raise RuntimeError("Device screenrecord did not start; see screenrecord.log")

    def stop_recording(self):
        if self.recording is None:
            return
        if self.recording.poll() is None and self.record_pid:
            self.adb("shell", "kill", "-2", self.record_pid, check=False)
        try:
            self.recording.wait(timeout=15)
        except subprocess.TimeoutExpired:
            self.recording.terminate()
            self.recording.wait(timeout=5)
            raise RuntimeError("screenrecord did not finish after SIGINT")
        finally:
            self.record_log.close()
        self.adb("pull", self.remote_video, str(self.video), timeout=30)
        with self.video.open("rb") as stream:
            header = stream.read(64)
        if self.video.stat().st_size < 1024 or b"ftyp" not in header:
            raise RuntimeError("screenrecord did not produce a valid MP4 container")
        self.result["recording_finished_at_utc"] = utc_now()
        self.result["recording_wall_seconds"] = round(time.monotonic() - self.record_started, 3)
        self.result["video_file"] = self.video.name
        self.result["video_bytes"] = self.video.stat().st_size
        self.event("recording_pulled", file=self.video.name, bytes=self.video.stat().st_size)
        self.recording = None

    def run(self):
        if self.saved_notes(check=False):
            raise RuntimeError("Refusing to modify an emulator containing existing notes")
        self.adb("shell", "am", "start", "-n", ACTIVITY)
        time.sleep(2)
        texts = self.texts()
        if "아직 메모가 없어요" not in texts or "0개" not in texts:
            raise RuntimeError("A fresh empty Stillnote app on a phone-sized emulator is required")
        self.start_recording()
        self.step("empty_app", lambda: self.screenshot("01-empty"), pause=3)

        def create_first():
            self.tap("새 메모")
            self.type_text("메모 제목", FIRST["title"])
            self.type_text("메모 내용", FIRST["body"])
            self.hide_keyboard()
            if "기기에 저장됨" not in self.texts():
                raise AssertionError("First note did not display the saved state")
            self.screenshot("02-first-note")
        self.step("create_and_edit_first_note", create_first)

        def pin_first():
            self.tap("메모 메뉴")
            self.tap("메모 고정")
            self.tap("닫기")
            self.tap("목록")
            self.assert_list(1, [FIRST["title"]], pinned=True)
        self.step("pin_first_note", pin_first)

        def create_second():
            self.tap("새 메모")
            self.type_text("메모 제목", SECOND["title"])
            self.type_text("메모 내용", SECOND["body"])
            self.hide_keyboard()
        self.step("create_and_edit_second_note", create_second)

        def all_notes():
            self.tap("목록")
            self.assert_list(2, [FIRST["title"], SECOND["title"]], pinned=True)
            self.assert_saved()
            self.screenshot("03-two-notes")
        self.step("two_notes_saved", all_notes)

        def search():
            self.type_text("메모 검색", "coffee")
            self.hide_keyboard()
            self.assert_list(1, [SECOND["title"]], absent=[FIRST["title"]])
            self.screenshot("04-search")
        self.step("search_coffee", search)

        def clear_search():
            self.tap("지우기")
            self.assert_list(2, [FIRST["title"], SECOND["title"]], pinned=True)
        self.step("clear_search", clear_search)

        def pinned_only():
            self.tap("고정 메모")
            self.assert_list(1, [FIRST["title"]], absent=[SECOND["title"]], pinned=True)
            self.screenshot("05-pinned-filter")
        self.step("filter_pinned_notes", pinned_only)

        def unfilter():
            self.tap("고정 메모")
            self.assert_list(2, [FIRST["title"], SECOND["title"]], pinned=True)
        self.step("clear_pinned_filter", unfilter)

        def relaunch():
            self.adb("shell", "am", "force-stop", PACKAGE)
            time.sleep(0.7)
            self.adb("shell", "am", "start", "-n", ACTIVITY)
            time.sleep(1.5)
            self.tap("목록")
            self.assert_list(2, [FIRST["title"], SECOND["title"]], pinned=True)
            self.assert_saved()
            self.screenshot("06-restored-after-relaunch")
        self.step("relaunch_and_verify_persistence", relaunch, pause=3)

    def finish(self, error=None):
        cleanup_errors = []
        if error is not None:
            self.result["error"] = str(error)
            (self.output / "failure-traceback.txt").write_text(traceback.format_exc(), encoding="utf-8")
            try:
                self.screenshot("failure-screen")
            except Exception as capture_error:
                cleanup_errors.append("failure screenshot: " + str(capture_error))
        try:
            self.stop_recording()
        except Exception as record_error:
            cleanup_errors.append("recording finalization: " + str(record_error))
        try:
            (self.output / "logcat.txt").write_text(
                self.adb("logcat", "-d", "-v", "threadtime", timeout=30), encoding="utf-8")
        except Exception as log_error:
            cleanup_errors.append("logcat: " + str(log_error))
        self.result["finished_at_utc"] = utc_now()
        self.result["passed"] = error is None and not cleanup_errors
        if cleanup_errors:
            self.result["cleanup_errors"] = cleanup_errors
        (self.output / "verification-results.json").write_text(
            json.dumps(self.result, ensure_ascii=False, indent=2), encoding="utf-8")
        return 0 if self.result["passed"] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    demo = None
    try:
        demo = Demo(args.output)
        demo.run()
    except Exception as error:
        if demo is not None:
            return demo.finish(error)
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "failure-traceback.txt").write_text(traceback.format_exc(), encoding="utf-8")
        (args.output / "verification-results.json").write_text(
            json.dumps({"passed": False, "finished_at_utc": utc_now(), "error": str(error)}, indent=2),
            encoding="utf-8")
        return 1
    return demo.finish()


if __name__ == "__main__":
    sys.exit(main())