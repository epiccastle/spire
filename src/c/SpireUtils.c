#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/socket.h>
#include <termios.h>
#include <sys/ioctl.h>
#include "SpireUtils.h"

JNIEXPORT jint JNICALL Java_SpireUtils_is_1a_1tty (JNIEnv *env, jclass this) {
  return (jint)isatty(STDOUT_FILENO);
}

static int get_win_size(int fd, struct winsize *win) {
  return ioctl(fd, TIOCGWINSZ, (char*)win);
}

JNIEXPORT jint JNICALL Java_SpireUtils_get_1terminal_1width (JNIEnv *env, jclass this) {
  struct winsize size;
  (void)get_win_size(STDOUT_FILENO, &size);
  return (jint)(size.ws_col);
}

JNIEXPORT jint JNICALL Java_SpireUtils_get_1terminal_1height (JNIEnv *env, jclass this) {
  struct winsize size;
  (void)get_win_size(STDOUT_FILENO, &size);
  return (jint)(size.ws_row);
}

/* move terminal into and out of raw mode for password entry */


static struct termios _saved_tio;
static int _in_raw_mode = 0;

struct termios *
get_saved_tio(void)
{
        return _in_raw_mode ? &_saved_tio : NULL;
}

void
leave_raw_mode(int quiet)
{
        if (!_in_raw_mode)
                return;
        if (tcsetattr(fileno(stdin), TCSADRAIN, &_saved_tio) == -1) {
                if (!quiet)
                        perror("tcsetattr");
        } else
                _in_raw_mode = 0;
}

void
enter_raw_mode(int quiet)
{
        struct termios tio;

        if (tcgetattr(fileno(stdin), &tio) == -1) {
                if (!quiet)
                        perror("tcgetattr");
                return;
        }
        _saved_tio = tio;
        tio.c_iflag |= IGNPAR;
        tio.c_iflag &= ~(ISTRIP | INLCR | IGNCR | ICRNL | IXON | IXANY | IXOFF);
#ifdef IUCLC
        tio.c_iflag &= ~IUCLC;
#endif
        tio.c_lflag &= ~(ISIG | ICANON | ECHO | ECHOE | ECHOK | ECHONL);
#ifdef IEXTEN
        tio.c_lflag &= ~IEXTEN;
#endif
        tio.c_oflag &= ~OPOST;
        tio.c_cc[VMIN] = 1;
        tio.c_cc[VTIME] = 0;
        if (tcsetattr(fileno(stdin), TCSADRAIN, &tio) == -1) {
                if (!quiet)
                        perror("tcsetattr");
        } else
                _in_raw_mode = 1;
}

/*
 * Class:     SpireUtils
 * Method:    enter_raw_mode
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_SpireUtils_enter_1raw_1mode(JNIEnv *env, jclass this, jint quiet)
{
  enter_raw_mode(quiet);
}

/*
 * Class:     SpireUtils
 * Method:    leave_raw_mode
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_SpireUtils_leave_1raw_1mode(JNIEnv *env, jclass this, jint quiet)
{
  leave_raw_mode(quiet);
}


/*
 * Copy string src to buffer dst of size dsize.  At most dsize-1
 * chars will be copied.  Always NUL terminates (unless dsize == 0).
 * Returns strlen(src); if retval >= dsize, truncation occurred.
 */
size_t
spire_strlcpy(char * __restrict dst, const char * __restrict src, size_t dsize)
{
        const char *osrc = src;
        size_t nleft = dsize;

        /* Copy as many bytes as will fit. */
        if (nleft != 0) {
                while (--nleft != 0) {
                        if ((*dst++ = *src++) == '\0')
                                break;
                }
        }

        /* Not enough room in dst, add NUL and traverse rest of src. */
        if (nleft == 0) {
                if (dsize != 0)
                        *dst = '\0';		/* NUL-terminate dst */
                while (*src++)
                        ;
        }

        return(src - osrc - 1);	/* count does not include NUL */
}

/* same error as openbsd ssh code uses */
#define SSH_ERR_SYSTEM_ERROR			-24

/*
 * Class:     SpireUtils
 * Method:    open_socket
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_SpireUtils_ssh_1open_1auth_1socket (JNIEnv *env, jclass this, jstring path) {
  const char* cpath = (*env)->GetStringUTFChars(env, path, 0);

  struct sockaddr_un sunaddr;
  memset(&sunaddr, 0, sizeof(sunaddr));
  sunaddr.sun_family = AF_UNIX;
  spire_strlcpy(sunaddr.sun_path, cpath, sizeof(sunaddr.sun_path));

  (*env)->ReleaseStringUTFChars(env, path, cpath);

  int sock = socket(AF_UNIX, SOCK_STREAM, 0);
  if(sock == -1)
    {
      // error: failed to allocate unix domain socket
      return (jint)SSH_ERR_SYSTEM_ERROR;
    }

  if(fcntl(sock, F_SETFD, FD_CLOEXEC) == -1 ||
     connect(sock, (struct sockaddr *)&sunaddr, sizeof(sunaddr)) == -1)
    {
      close(sock);
      return (jint)SSH_ERR_SYSTEM_ERROR;
    }

  return (jint)sock;
}

/*
 * Class:     SpireUtils
 * Method:    ssh_close_auth_socket
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_SpireUtils_ssh_1close_1auth_1socket(JNIEnv *env, jclass this, jint socket)
{
  close((int)socket);
}

/*
 * Class:     SpireUtils
 * Method:    ssh_auth_socket_read
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_SpireUtils_ssh_1auth_1socket_1read
(JNIEnv *env, jclass this, jint fd, jbyteArray buf, jint count)
{
  jbyte buffer[count];
  int bytes_read = read(fd, (void *)buffer, count);
  (*env)->SetByteArrayRegion(env, buf, 0, bytes_read, buffer);
  return bytes_read;
}

/*
 * Class:     SpireUtils
 * Method:    ssh_auth_socket_write
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_SpireUtils_ssh_1auth_1socket_1write
(JNIEnv *env, jclass this, jint fd, jbyteArray buf, jint count)
{
  jboolean copy = 1;
  jbyte *buffer = (*env)->GetByteArrayElements(env, buf, &copy);
  int bytes_writen = write(fd, (const void *)buffer, count);
  (*env)->ReleaseByteArrayElements(env, buf, buffer, JNI_ABORT);
  return bytes_writen;
}
