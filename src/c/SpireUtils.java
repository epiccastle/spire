public class SpireUtils {
    // terminal size queries
    public static native int get_terminal_width();
    public static native int get_terminal_height();

    // unix domain socket access
    public static native int ssh_open_auth_socket(String path);
    public static native void ssh_close_auth_socket(int socket);
    public static native int ssh_auth_socket_read(int fd, byte[] buf, int count);
    public static native int ssh_auth_socket_write(int fd, byte[] buf, int count);
}
