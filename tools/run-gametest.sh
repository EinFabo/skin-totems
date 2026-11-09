#!/bin/sh
# Startet den Client-Spieltest unsichtbar (virtueller X-Bildschirm :99) und stumm.
# Xvfb liegt ohne Root unter ~/.local/opt/xvfb (per apt-get download + dpkg -x entpackt);
# /usr/bin/xkbcomp und /tmp/.X11-unix werden dafür in einem User-Namespace eingeblendet.
set -e
D="$HOME/.local/opt/xvfb"
if ! grep -q "@/tmp/.X11-unix/X99" /proc/net/unix; then
	unshare -Urm sh -c "mount -t tmpfs tmpfs /tmp/.X11-unix && \
		mount -t overlay overlay -o lowerdir=/usr/bin:$D/root/usr/bin /usr/bin && \
		LD_LIBRARY_PATH=$D/root/usr/lib/x86_64-linux-gnu exec $D/root/usr/bin/Xvfb :99 \
		-screen 0 1280x720x24 -nolisten tcp -xkbdir $D/root/usr/share/X11/xkb" >/dev/null 2>&1 &
	sleep 2
fi
cd "$(dirname "$0")/.."
exec ./gradlew runClientGameTest -PtestDisplay=:99 "$@"
