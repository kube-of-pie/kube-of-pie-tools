<#ftl output_format="plainText" strip_whitespace=true>
#! /bin/sh

# The initialization script is launched at first startup by headless_unattended OpenRC service.
#
# It configures:
# - keyboard layout.
# - network: interfaces, Wifi SSID/passphrase, DNS, etc.
# - remote access through SSH
# - users: password, SSH keys, etc.
# - time synchronization
#
# Then it persists everything it has configured on the sys disk (using the setup-disk utility) and reboot
# the machine.
#
# An error in this script will cause the script to not continue and pause where it failed.
# You can access the log through syslog: tail /var/log/messages.

# The following comment prevent the headless overlay to configure a SSH server.
#NO_SSH

# Configuration variables
# Generated from kube-of-pie cluster config by :imagegenerator.

KEYMAP="<#if keymap??>${keymap}</#if>"
HOSTNAME="${hostname}"
INTERFACES="${interfaces}"
WIFI_ENABLED="<#if wifiEnabled>1</#if>"
WPA_SUPPLICANT_SSID="<#if wifiSsid??>${wifiSsid}</#if>"
WPA_SUPPLICANT_PASSPHRASE="<#if wifiPassphrase??>${wifiPassphrase}</#if>"
DNS="<#if dns??>${dns}</#if>"
USERS="<#if users?has_content>${users?map(u -> u.name)?join(" ")}</#if>"
<#list users as user>
<#if user.password??>
USER_${user.name}_PASSWORD="${user.password}"
</#if>
<#if user.sshPublicKey??>
USER_${user.name}_SSH_KEY="${user.sshPublicKey}"
</#if>
</#list>
SSHD_ENABLED="<#if sshdEnabled>1</#if>"
TIMEZONE="<#if timezone??>${timezone}</#if>"
NTP="<#if ntp??>${ntp}</#if>"
ADDITIONAL_KERNEL_ARGS="<#if additionalKernelArgs??>${additionalKernelArgs}</#if>"
<#noparse>
# Main
# The order follows the one described in Alpine wiki:
# https://wiki.alpinelinux.org/wiki/Alpine_setup_scripts#setup-alpine

# Redirect error output to syslog.
exec 2>&1

# Uncomment to enable stdout and errors redirection to console (service won't show messages)
# exec 1>/dev/console 2>&1
# You can obtain the log through syslog:
# tail /var/log/messages

# Functions

# Log message both into the syslog and into the console.
_logger() {
  logger -st "${0##*/}" "$1"
  echo "${0##*/}: $1" > /dev/console
}

_configure_user() {
  USER="$1"
  _logger "Setting-up user $USER."

  # Create the user if it does not exists.
  id "$USER" >/dev/null 2>&1;
  if [ $? -ne 0 ]; then
    # Create the user.
    setup-user -a -u "$USER"
  fi

  # Determine user home directory.
  # eval is bad but busybox does not have a full bash.
  USER_HOME=$(eval "echo ~$USER")

  # Setup its password with the content of USER_${USER}_PASSWORD env var.
  # eval is bad but busybox does not have a full bash.
  USER_PASSWORD=$(eval "echo \$USER_${USER}_PASSWORD")
  if [ -n "$USER_PASSWORD" ]; then
    echo "$USER:$USER_PASSWORD" | chpasswd
  fi
}

_configure_user_home() {
  USER="$1"
  SYSTEM_ROOT="$2"

  # Determine user home directory.
  # eval is bad but busybox does not have a full bash.
  USER_HOME=$(eval "echo ~$USER")
  USER_HOME="$SYSTEM_ROOT/$USER_HOME"

  _logger "Setting-up user home for $USER: $USER_HOME."

  # Create the home directory if it does not exists.
  if [ -e "$USER_HOME" ]; then
    # Create the user's home directory.
    mkdir "$USER_HOME"
    chown $USER:$USER "$USER_HOME"
  fi

  # Create authorized SSH keys.
  USER_SSH_KEY=$(eval "echo \$USER_${USER}_SSH_KEY")
  if [ -n "$USER_SSH_KEY" ]; then
    mkdir $USER_HOME/.ssh
    echo "$USER_SSH_KEY" > "$USER_HOME/.ssh/authorized_keys"
    chown -R $USER:$USER "$USER_HOME/.ssh"
  fi
}

# Main

_logger "Starting headless configuration."

if [ -n "$KEYMAP" ]; then
  _logger "Setting-up keyboard mapping to $KEYMAP."
  setup-keymap $KEYMAP
fi

if [ -n "$HOSTNAME" ]; then
  _logger "Setting-up hostname to $HOSTNAME."
  setup-hostname "$HOSTNAME"
fi

if [ "$WIFI_ENABLED" -eq "1" ]; then
  _logger "Setting-up Wifi."

  # Install wpa_supplicant package.
  apk add wpa_supplicant

  # Configure wifi network to connect to.
  wpa_passphrase "$WPA_SUPPLICANT_SSID" "$WPA_SUPPLICANT_PASSPHRASE" > /etc/wpa_supplicant/wpa_supplicant.conf

  # Restart wpa_supplicant service
  rc-service wpa_supplicant restart

  # Adding wpa_supplicant to boot services
  rc-update add wpa_supplicant boot
fi

_logger "Setting-up network"

# Adding networking to boot services
rc-update add networking boot

# Setting-up network interfaces
if [ -n "$INTERFACES" ]; then
  echo "$INTERFACES" | setup-interfaces -i
fi

_logger "Restarting networking to apply changes to configuration."
rc-service networking --quiet restart

if [ -n "$DNS" ]; then
  _logger "Setting-up DNS server to $DNS."
  setup-dns "$DNS"
fi

if [ -n "$TIMEZONE" ]; then
  _logger "Setting-up timezone to $TIMEZONE."
  setup-timezone -z "$TIMEZONE"
fi

_logger "Restarting hostname service apply changes."
rc-service hostname --quiet restart
# Sleep a moment otherwise nothing is logged since hostname will also restart syslog service.
sleep 5s

if [ -n "$USERS" ]; then
  _logger "Setting-up users: $USERS."
  for USER in $USERS; do
    _configure_user "$USER"
  done
fi

if [ "$SSHD_ENABLED" -eq "1" ]; then
  _logger "Setting-up remote access through SSH."
  setup-sshd -c openssh
fi

if [ -n "$NTP" ]; then
  _logger "Setting-up NTP time synchronization."
  setup-ntp -c "$NTP"
fi

_logger "Setting-up apk repositories."
setup-apkrepos -1

_logger "Setting-up system on boot media."

# Stopping modloop to make current disk unbusy.
rc-service modloop stop

#Unmounting boot partition
umount /dev/mmcblk0p1

# Install the system on the disk
# The ERASE_DISKS env var prevent the utility to ask for confirmation when erasing the disk
ERASE_DISKS=/dev/mmcblk0 setup-disk -q -m sys /dev/mmcblk0

_logger "Mounting new system partitions."
mkdir /media/mmcblk0p2
mount /dev/mmcblk0p1 /media/mmcblk0p1
mount /dev/mmcblk0p2 /media/mmcblk0p2

if [ -n "$ADDITIONAL_KERNEL_ARGS" ]; then
  _logger "Setting-up additional kernel args: $ADDITIONAL_KERNEL_ARGS."
  CMDLINE=$(head -n 1 /media/mmcblk0p1/cmdline.txt)
  echo "$CMDLINE $ADDITIONAL_KERNEL_ARGS" > /media/mmcblk0p1/cmdline.txt
fi

if [ -n "$USERS" ]; then
  _logger "Setting-up users' home: $USERS."
  for USER in $USERS; do
    _configure_user_home "$USER" "/media/mmcblk0p2"
  done
fi

_logger "Rebooting to finish headless configuration"
reboot
</#noparse>
