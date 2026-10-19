;;; Common Lisp/SBCL launch-script regression tests. No external Lisp libraries.
(require :asdf)
(defvar *root*)
(defvar *environment*)
(defvar *passed* 0)
(defvar *failed* 0)
(defparameter *source* (merge-pathnames "../run.sh" (uiop:pathname-directory-pathname *load-truename*)))
(defun write-file (relative content &optional executable)
  (let ((path (merge-pathnames relative *root*)))
    (ensure-directories-exist path)
    (with-open-file (out path :direction :output :if-exists :supersede) (write-string content out))
    (when executable (uiop:run-program (list "chmod" "+x" (namestring path))))))
(defun text-of (relative)
  (let ((path (merge-pathnames relative *root*)))
    (if (probe-file path) (uiop:read-file-string path) "")))
(defun check (value message) (unless value (error "~a" message)))
(defun contains (relative text) (search text (text-of relative)))
(defun launch (args &rest environment)
  (nth-value 2 (uiop:run-program (append (list "env") *environment* environment
                                       (list "bash" (namestring (merge-pathnames "scripts/run.sh" *root*))) args)
                                :directory "/tmp/" :output :string :error-output :string :ignore-error-status t)))
(defun fixture ()
  (ensure-directories-exist (merge-pathnames "scripts/" *root*))
  (uiop:copy-file *source* (merge-pathnames "scripts/run.sh" *root*))
  (ensure-directories-exist (merge-pathnames "web/node_modules/" *root*))
  (dolist (tool '("java" "node" "npm"))
    (write-file (concatenate 'string "bin/" tool) (format nil "#!/bin/sh~%exit 0~%") t))
  (write-file "gradlew" "#!/bin/sh
printf '%s\\n' \"$@\" > \"$RUN_ARGS_LOG\"
exit \"${FAKE_RUN_EXIT:-0}\"
" t)
  (write-file "scripts/build-mods.sh" "#!/bin/sh
if [ -f \"$RUN_ARGS_LOG\" ]; then exit 99; fi
printf '%s\\n' \"$@\" > \"$RUN_ARGS_LOG.mods\"
exit \"${FAKE_MOD_EXIT:-0}\"
" t)
  (write-file "bin/adb" "#!/bin/sh
printf '%s\\n' \"$*\" >> \"$ADB_CALLS_LOG\"
if [ \"$1\" = '-d' ]; then printf 'test-usb-device\\n'; exit 0; fi
if [ \"$4\" = '--list' ]; then printf '%s\\n' \"${ADB_EXISTING:-}\"; fi
" t))
(defun run-test (name function)
  (let* ((*root* (merge-pathnames (format nil "craftlink run ~d-~d/" (get-universal-time) (random 1000000000)) (uiop:temporary-directory)))
         (*environment* (list (format nil "PATH=~abin:~a" *root* (uiop:getenv "PATH"))
                              (format nil "RUN_ARGS_LOG=~aargs" *root*) (format nil "ADB_CALLS_LOG=~aadb_calls" *root*))))
    (unwind-protect
         (handler-case (progn (fixture) (funcall function) (incf *passed*) (format t "PASS ~a~%" name))
           (error (condition) (incf *failed*) (format *error-output* "FAIL ~a: ~a~%" name condition)))
      (when (probe-file *root*) (uiop:delete-directory-tree *root* :validate t :if-does-not-exist :ignore)))))
(run-test "normal offline launch"
  (lambda ()
    (check (zerop (launch '("--offline"))) "Launch failed")
    (dolist (arg '("runClient" "-PcraftlinkTransport=lan" "--offline" "-PwebOffline=true" "-Dnet.minecraftforge.gradle.check.certs=false")) (check (contains "args" arg) arg))
    (check (not (probe-file (merge-pathnames "adb_calls" *root*))) "LAN used adb")))
(run-test "online launch keeps the Forge certificate preflight enabled"
  (lambda () (check (zerop (launch nil)) "Launch failed")
    (check (not (contains "args" "check.certs=false")) "Online certificate preflight disabled")))
(run-test "USB cleanup after launch failure"
  (lambda ()
    (check (= 7 (launch '("usb" "--port" "9876") "FAKE_RUN_EXIT=7")) "Lost exit status")
    (check (contains "adb_calls" "reverse --no-rebind tcp:9876 tcp:9876") "No forwarding")
    (check (contains "adb_calls" "reverse --remove tcp:9876") "No cleanup")
    (check (contains "args" "-PcraftlinkPort=9876") "Wrong port")
    (check (contains "args" "-PcraftlinkTransport=usb") "Wrong transport")))
(run-test "existing matching forwarding is retained"
  (lambda ()
    (check (zerop (launch '("usb") "ADB_EXISTING=UsbFfs tcp:8765 tcp:8765")) "Launch failed")
    (check (not (contains "adb_calls" "--no-rebind")) "Existing mapping replaced")
    (check (not (contains "adb_calls" "--remove")) "Existing mapping removed")))
(run-test "conflicting forwarding is preserved"
  (lambda ()
    (check (not (zerop (launch '("usb") "ADB_EXISTING=UsbFfs tcp:8765 tcp:9999"))) "Conflict accepted")
    (check (not (probe-file (merge-pathnames "args" *root*))) "Game launched on conflict")
    (check (not (contains "adb_calls" "--no-rebind")) "Conflict overwritten")
    (check (not (contains "adb_calls" "--remove")) "Conflict removed")))
(run-test "USB prepare needs no phone"
  (lambda ()
    (check (zerop (launch '("usb" "--prepare"))) "Prepare failed")
    (check (contains "args" "prepareRunClient") "Missing prepare task")
    (check (contains "args" "classes") "Missing web/Java compilation")
    (check (not (probe-file (merge-pathnames "adb_calls" *root*))) "Prepare used adb")))
(run-test "invalid port stops before launch"
  (lambda ()
    (check (= 2 (launch '("usb" "--port" "65536"))) "Invalid port accepted")
    (check (not (probe-file (merge-pathnames "args" *root*))) "Invalid launch")
    (check (not (probe-file (merge-pathnames "adb_calls" *root*))) "Invalid adb operation")))
(run-test "development launch enables authenticated account by default"
  (lambda ()
    (check (zerop (launch '("--prepare"))) "Prepare failed")
    (check (contains "args" "-PcraftlinkLogin=true") "Missing Microsoft login integration")))
(run-test "explicit guest launch skips authentication"
  (lambda ()
    (check (zerop (launch '("--no-login"))) "Guest launch failed")
    (check (not (contains "args" "-PcraftlinkLogin=true")) "Guest launch enabled login")))
(run-test "mods are refreshed before authenticated launch"
  (lambda ()
    (check (zerop (launch '("--mods" "--offline"))) "MOD launch failed")
    (check (contains "args.mods" "--offline") "Offline not forwarded to MOD build")
    (check (contains "args" "-PcraftlinkModpack=true") "MOD runtime not selected")
    (check (contains "args" "-PcraftlinkLogin=true") "Authentication not retained")))
(run-test "failed MOD build prevents stale launch"
  (lambda ()
    (check (= 13 (launch '("--mods") "FAKE_MOD_EXIT=13")) "MOD failure exit status lost")
    (check (not (probe-file (merge-pathnames "args" *root*))) "Started Minecraft with stale MODs")))
(run-test "vanilla launch does not build optional MODs"
  (lambda ()
    (check (zerop (launch '("--no-login"))) "Vanilla launch failed")
    (check (not (probe-file (merge-pathnames "args.mods" *root*))) "Vanilla launch built optional MODs")))
(run-test "cached identity skips DevAuth and keeps offline dependency mode"
  (lambda ()
    (write-file ".craftlink/identity.json" "{}")
    (check (zerop (launch '("--offline" "--cached-login"))) "Cached launch failed")
    (check (contains "args" "-PcraftlinkCachedLogin=true") "Cached identity not selected")
    (check (contains "args" "-PcraftlinkLogin=false") "DevAuth not explicitly disabled")
    (check (not (contains "args" "-PcraftlinkLogin=true")) "Cached launch enabled DevAuth")))
(run-test "missing cached identity stops before optional MOD rebuild"
  (lambda ()
    (check (= 1 (launch '("--mods" "--cached-login"))) "Missing identity was accepted")
    (check (not (probe-file (merge-pathnames "args.mods" *root*))) "Unnecessary MOD build")
    (check (not (probe-file (merge-pathnames "args" *root*))) "Guest launch silently substituted")))
(format t "~d passed, ~d failed~%" *passed* *failed*)
(uiop:quit (if (zerop *failed*) 0 1))
