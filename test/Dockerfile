FROM alpine:3.16.2
ARG root_password
RUN apk update
RUN apk add openssh-server openssh file curl netcat-openbsd
RUN sed -E "s/^#*(PermitRootLogin ).+$/\1yes/" /etc/ssh/sshd_config -i
RUN sed -E "s/^#*(AllowTcpForwarding ).+$/\1yes/" /etc/ssh/sshd_config -i
RUN ssh-keygen -A
# Install [gost](https://github.com/ginuerzh/gost) for testing proxy support.
# Run this before changing root password to preseve the docker layer caching as
# much as possible across tests.
RUN wget -O /tmp/gost.gz https://github.com/ginuerzh/gost/releases/download/v2.11.5/gost-linux-amd64-2.11.5.gz \
    && gunzip -c -k /tmp/gost.gz > /usr/local/bin/gost \
    && chmod +x /usr/local/bin/gost \
    && rm -f /tmp/gost.gz
ENTRYPOINT ["/usr/sbin/sshd","-D","-E","/tmp/sshd.log"]
EXPOSE 22/tcp
RUN echo "root:$root_password"|chpasswd
