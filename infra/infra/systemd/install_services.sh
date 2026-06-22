#!/bin/bash

echo "============================================"
echo "Installing PSP Switch Systemd Services"
echo "============================================"

if [ "$EUID" -ne 0 ]; then
  echo "Please run as root (use sudo)"
  exit
fi

# Ensure directories exist
mkdir -p /opt/APEX-UPI
echo "Note: Make sure your repository is cloned or moved to /opt/APEX-UPI"
echo "If your code is elsewhere, you MUST edit the WorkingDirectory in the .service files before running this script."

echo "Copying service files to /etc/systemd/system/..."
cp *.service /etc/systemd/system/

echo "Reloading systemd daemon..."
systemctl daemon-reload

echo "Enabling services to start on boot..."
systemctl enable psp-tpap-ingress.service
systemctl enable psp-orchestrator.service
systemctl enable psp-npci-adapter.service
systemctl enable psp-tpap-egress.service
systemctl enable psp-npci-response.service
systemctl enable psp-ledger.service

echo "Starting all services..."
systemctl start psp-tpap-ingress.service
systemctl start psp-orchestrator.service
systemctl start psp-npci-adapter.service
systemctl start psp-tpap-egress.service
systemctl start psp-npci-response.service
systemctl start psp-ledger.service

echo "============================================"
echo "Installation complete!"
echo "Check status with: systemctl status psp-*"
echo "View logs with: journalctl -u psp-tpap-ingress -f"
echo "============================================"
