#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <termios.h>
#include <sys/ioctl.h>

static int get_win_size(int fd, struct winsize *win) {
  return ioctl(fd, TIOCGWINSZ, (char*)win);
}

int main() {
  struct winsize size;
  if(!get_win_size(STDOUT_FILENO, &size)) {
    printf("width: %d\nheight: %d\n", size.ws_row, size.ws_col);
    return 0;
  } else {
    printf("error!\n");
  }

  return 1;
}
