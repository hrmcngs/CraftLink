;;; Common Lisp / SBCL tests. cloudflared is mocked: no external tunnel is created.
(require :asdf)
(defparameter *source* (merge-pathnames "../tunnel.sh" (uiop:pathname-directory-pathname *load-truename*)))
(defvar *root*)
(defvar *passed* 0)
(defvar *failed* 0)
(defun check (condition text) (unless condition (error "~a" text)))
(defun launch (args &optional (exit "0"))
  (nth-value 2 (uiop:run-program
    (append (list "env" (format nil "PATH=~abin" *root*) (format nil "MOCK_TUNNEL_LOG=~acalls" *root*) (concatenate 'string "MOCK_TUNNEL_EXIT=" exit)
                  "/bin/bash" (namestring (merge-pathnames "tunnel.sh" *root*))) args)
    :directory "/tmp/" :output :string :error-output :string :ignore-error-status t)))
(defun calls () (let ((file (merge-pathnames "calls" *root*))) (if (probe-file file) (uiop:read-file-string file) "")))
(defun run-test (name body)
  (let ((*root* (merge-pathnames (format nil "craftlink tunnel ~d-~d/" (get-universal-time) (random 1000000000)) (uiop:temporary-directory))))
    (unwind-protect
      (handler-case
        (progn
          (ensure-directories-exist (merge-pathnames "bin/" *root*))
          (uiop:copy-file *source* (merge-pathnames "tunnel.sh" *root*))
          (with-open-file (out (merge-pathnames "bin/cloudflared" *root*) :direction :output :if-exists :supersede)
            (write-string "#!/bin/sh
printf '%s\\n' \"$@\" > \"$MOCK_TUNNEL_LOG\"
exit \"${MOCK_TUNNEL_EXIT:-0}\"
" out))
          (uiop:run-program (list "chmod" "+x" (namestring (merge-pathnames "bin/cloudflared" *root*))))
          (funcall body) (incf *passed*) (format t "PASS ~a~%" name))
        (error (condition) (incf *failed*) (format *error-output* "FAIL ~a: ~a~%" name condition)))
      (when (probe-file *root*) (uiop:delete-directory-tree *root* :validate t :if-does-not-exist :ignore)))))
(run-test "controller tunnel uses only localhost and disables self-update"
  (lambda () (check (zerop (launch nil)) "Launch failed")
    (check (equal (calls) (format nil "tunnel~%--no-autoupdate~%--url~%http://127.0.0.1:8765~%")) "Unexpected command")))
(run-test "classroom tunnel uses its own port"
  (lambda () (check (zerop (launch '("classroom"))) "Launch failed") (check (search "http://127.0.0.1:8766" (calls)) "Wrong classroom port")))
(run-test "custom port strips leading zeroes"
  (lambda () (check (zerop (launch '("classroom" "--port" "09876"))) "Launch failed") (check (search "http://127.0.0.1:9876" (calls)) "Wrong custom port")))
(run-test "invalid modes and ports never start a tunnel"
  (lambda () (dolist (args '(("unknown") ("controller" "--port" "65536") ("classroom" "--port" "0") ("controller" "--port") ("controller" "--port" "$(touch bad)")))
    (check (= 2 (launch args)) "Invalid input accepted")) (check (equal "" (calls)) "Tunnel ran on invalid input")))
(run-test "cloudflared failure exit is preserved"
  (lambda () (check (= 7 (launch '("controller") "7")) "Lost exit status")))
(run-test "missing cloudflared provides setup error"
  (lambda () (delete-file (merge-pathnames "bin/cloudflared" *root*)) (check (= 1 (launch '("controller"))) "Missing dependency ignored") (check (equal "" (calls)) "Unexpected execution")))
(format t "~d passed, ~d failed~%" *passed* *failed*)
(uiop:quit (if (zerop *failed*) 0 1))
