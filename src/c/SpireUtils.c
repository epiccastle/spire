#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/socket.h>
#include <termios.h>
#include <sys/ioctl.h>
#include "SpireUtils.h"

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

#define SSH_ERR_SUCCESS				0
#define SSH_ERR_INTERNAL_ERROR			-1
#define SSH_ERR_ALLOC_FAIL			-2
#define SSH_ERR_MESSAGE_INCOMPLETE		-3
#define SSH_ERR_INVALID_FORMAT			-4
#define SSH_ERR_BIGNUM_IS_NEGATIVE		-5
#define SSH_ERR_STRING_TOO_LARGE		-6
#define SSH_ERR_BIGNUM_TOO_LARGE		-7
#define SSH_ERR_ECPOINT_TOO_LARGE		-8
#define SSH_ERR_NO_BUFFER_SPACE			-9
#define SSH_ERR_INVALID_ARGUMENT		-10
#define SSH_ERR_KEY_BITS_MISMATCH		-11
#define SSH_ERR_EC_CURVE_INVALID		-12
#define SSH_ERR_KEY_TYPE_MISMATCH		-13
#define SSH_ERR_KEY_TYPE_UNKNOWN		-14 /* XXX UNSUPPORTED? */
#define SSH_ERR_EC_CURVE_MISMATCH		-15
#define SSH_ERR_EXPECTED_CERT			-16
#define SSH_ERR_KEY_LACKS_CERTBLOB		-17
#define SSH_ERR_KEY_CERT_UNKNOWN_TYPE		-18
#define SSH_ERR_KEY_CERT_INVALID_SIGN_KEY	-19
#define SSH_ERR_KEY_INVALID_EC_VALUE		-20
#define SSH_ERR_SIGNATURE_INVALID		-21
#define SSH_ERR_LIBCRYPTO_ERROR			-22
#define SSH_ERR_UNEXPECTED_TRAILING_DATA	-23
#define SSH_ERR_SYSTEM_ERROR			-24
#define SSH_ERR_KEY_CERT_INVALID		-25
#define SSH_ERR_AGENT_COMMUNICATION		-26
#define SSH_ERR_AGENT_FAILURE			-27
#define SSH_ERR_DH_GEX_OUT_OF_RANGE		-28
#define SSH_ERR_DISCONNECTED			-29
#define SSH_ERR_MAC_INVALID			-30
#define SSH_ERR_NO_CIPHER_ALG_MATCH		-31
#define SSH_ERR_NO_MAC_ALG_MATCH		-32
#define SSH_ERR_NO_COMPRESS_ALG_MATCH		-33
#define SSH_ERR_NO_KEX_ALG_MATCH		-34
#define SSH_ERR_NO_HOSTKEY_ALG_MATCH		-35
#define SSH_ERR_NO_HOSTKEY_LOADED		-36
#define SSH_ERR_PROTOCOL_MISMATCH		-37
#define SSH_ERR_NO_PROTOCOL_VERSION		-38
#define SSH_ERR_NEED_REKEY			-39
#define SSH_ERR_PASSPHRASE_TOO_SHORT		-40
#define SSH_ERR_FILE_CHANGED			-41
#define SSH_ERR_KEY_UNKNOWN_CIPHER		-42
#define SSH_ERR_KEY_WRONG_PASSPHRASE		-43
#define SSH_ERR_KEY_BAD_PERMISSIONS		-44
#define SSH_ERR_KEY_CERT_MISMATCH		-45
#define SSH_ERR_KEY_NOT_FOUND			-46
#define SSH_ERR_AGENT_NOT_PRESENT		-47
#define SSH_ERR_AGENT_NO_IDENTITIES		-48
#define SSH_ERR_BUFFER_READ_ONLY		-49
#define SSH_ERR_KRL_BAD_MAGIC			-50
#define SSH_ERR_KEY_REVOKED			-51
#define SSH_ERR_CONN_CLOSED			-52
#define SSH_ERR_CONN_TIMEOUT			-53
#define SSH_ERR_CONN_CORRUPT			-54
#define SSH_ERR_PROTOCOL_ERROR			-55
#define SSH_ERR_KEY_LENGTH			-56
#define SSH_ERR_NUMBER_TOO_LARGE		-57
#define SSH_ERR_SIGN_ALG_UNSUPPORTED		-58

/*
 * Copy string src to buffer dst of size dsize.  At most dsize-1
 * chars will be copied.  Always NUL terminates (unless dsize == 0).
 * Returns strlen(src); if retval >= dsize, truncation occurred.
 */
size_t
strlcpy(char * __restrict dst, const char * __restrict src, size_t dsize)
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
  strlcpy(sunaddr.sun_path, cpath, sizeof(sunaddr.sun_path));

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
