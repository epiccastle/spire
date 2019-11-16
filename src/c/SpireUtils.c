#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <termios.h>
#include <sys/ioctl.h>
#include "SpireUtils.h"

static int get_win_size(int fd, struct winsize *win) {
  return ioctl(fd, TIOCGWINSZ, (char*)win);
}

JNIEXPORT jint JNICALL Java_SpireUtils_get_1terminal_1width (JNIEnv *env, jclass obj) {
  struct winsize size;
  (void)get_win_size(STDOUT_FILENO, &size);
  return (jint)(size.ws_col);
}

JNIEXPORT jint JNICALL Java_SpireUtils_get_1terminal_1height (JNIEnv *env, jclass obj) {
  struct winsize size;
  (void)get_win_size(STDOUT_FILENO, &size);
  return (jint)(size.ws_row);
}
