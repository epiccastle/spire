ARG base_image
FROM ${base_image}
ARG root_password
RUN if command -v apk > /dev/null; then \
      apk update && apk add \
        openssh-server openssh file curl netcat-openbsd \
        fish zsh dash; \
    elif command -v apt-get > /dev/null; then \
      apt-get update && apt-get install -y \
        openssh-server file curl netcat-openbsd \
        fish zsh ksh dash; \
    elif command -v dnf > /dev/null; then \
      dnf install -y --allowerasing openssh-server file curl nmap-ncat zsh ksh \
      && { \
           . /etc/os-release; \
           case "$ID $VERSION_ID $ID_LIKE" in \
             *amzn*|*"amazon linux"*) \
               echo "Amazon Linux: skipping nushell/fish/dash/ash (not in repos)";; \
             *fedora*|*rhel*|*centos*) \
               dnf install -y nushell fish dash || true;; \
             *) \
               dnf install -y nushell fish dash || true;; \
           esac; \
         }; \
    elif command -v pacman > /dev/null; then \
      pacman -Syu --noconfirm \
        openssh file curl openbsd-netcat \
        nushell fish zsh ksh dash; \
    fi
RUN sed -E "s/^#*(PermitRootLogin ).+$/\1yes/" /etc/ssh/sshd_config -i
RUN sed -E "s/^#*(AllowTcpForwarding ).+$/\1yes/" /etc/ssh/sshd_config -i
RUN sed -E "s/^#*(PasswordAuthentication ).+$/\1yes/" /etc/ssh/sshd_config -i
RUN ssh-keygen -A
RUN for shell in fish zsh ksh dash ash nu; do \
      shell_bin=$(command -v "$shell" 2>/dev/null) && \
      [ -n "$shell_bin" ] || continue; \
      if ! grep -qF "$shell_bin" /etc/shells 2>/dev/null; then \
        echo "$shell_bin" >> /etc/shells; \
      fi; \
      useradd -m -s "$shell_bin" "$shell" 2>/dev/null || \
      adduser -D -s "$shell_bin" "$shell" 2>/dev/null || true; \
      echo "$shell:${root_password}" | chpasswd; \
    done
ENTRYPOINT ["/usr/sbin/sshd","-D","-E","/tmp/sshd.log"]
EXPOSE 22/tcp
RUN echo "root:$root_password"|chpasswd
RUN mkdir -p /run/sshd
