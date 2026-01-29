#
# Makefile for mod_unimrcp FreeSWITCH module
#
# This Makefile builds the UniMRCP module for FreeSWITCH.
#
# Prerequisites:
# - FreeSWITCH development headers
# - UniMRCP client library
# - APR (Apache Portable Runtime)
# - APR-Util
#

# Module name
MODULE_NAME = mod_unimrcp

# Compiler settings
CC = gcc
CFLAGS = -Wall -Wextra -O2 -fPIC -g
LDFLAGS = -shared

# FreeSWITCH paths (adjust as needed)
FREESWITCH_PREFIX ?= /usr/local/freeswitch
FREESWITCH_INCLUDES ?= $(FREESWITCH_PREFIX)/include/freeswitch
FREESWITCH_LIBDIR ?= $(FREESWITCH_PREFIX)/lib
FREESWITCH_MODDIR ?= $(FREESWITCH_PREFIX)/mod

# UniMRCP paths (adjust as needed)
UNIMRCP_PREFIX ?= /usr/local/unimrcp
UNIMRCP_INCLUDES ?= $(UNIMRCP_PREFIX)/include
UNIMRCP_LIBDIR ?= $(UNIMRCP_PREFIX)/lib

# APR paths
APR_INCLUDES ?= /usr/include/apr-1
APR_LIBDIR ?= /usr/lib

# Include paths
INCLUDES = -I./include \
           -I$(FREESWITCH_INCLUDES) \
           -I$(UNIMRCP_INCLUDES) \
           -I$(APR_INCLUDES)

# Library paths and libraries
LIBS = -L$(FREESWITCH_LIBDIR) \
       -L$(UNIMRCP_LIBDIR) \
       -L$(APR_LIBDIR) \
       -lunimrcpclient \
       -lmrcp \
       -lmpf \
       -lapt \
       -lapr-1 \
       -laprutil-1 \
       -lpthread

# Source files
SRCS = src/mod_unimrcp.c \
       src/asr.c \
       src/tts.c

# Object files
OBJS = $(SRCS:.c=.o)

# Module output
MODULE = $(MODULE_NAME).so

# Default target
all: $(MODULE)

# Build module
$(MODULE): $(OBJS)
	$(CC) $(LDFLAGS) -o $@ $(OBJS) $(LIBS)
	@echo "Build complete: $(MODULE)"

# Compile source files
%.o: %.c
	$(CC) $(CFLAGS) $(INCLUDES) -c $< -o $@

# Install module
install: $(MODULE)
	@echo "Installing $(MODULE) to $(FREESWITCH_MODDIR)..."
	install -d $(FREESWITCH_MODDIR)
	install -m 755 $(MODULE) $(FREESWITCH_MODDIR)/
	@echo "Installing configuration files..."
	install -d $(FREESWITCH_PREFIX)/conf/autoload_configs
	install -m 644 conf/unimrcp.conf.xml $(FREESWITCH_PREFIX)/conf/autoload_configs/
	install -m 644 conf/unimrcp_client.xml $(FREESWITCH_PREFIX)/conf/
	@echo "Installation complete!"
	@echo ""
	@echo "To enable the module, add to modules.conf.xml:"
	@echo "  <load module=\"mod_unimrcp\"/>"

# Uninstall module
uninstall:
	@echo "Uninstalling $(MODULE)..."
	rm -f $(FREESWITCH_MODDIR)/$(MODULE)
	rm -f $(FREESWITCH_PREFIX)/conf/autoload_configs/unimrcp.conf.xml
	rm -f $(FREESWITCH_PREFIX)/conf/unimrcp_client.xml
	@echo "Uninstallation complete!"

# Clean build artifacts
clean:
	rm -f $(OBJS) $(MODULE)
	rm -f src/*.o

# Clean everything including backups
distclean: clean
	rm -f *~ src/*~ include/*~ conf/*~

# Show configuration
config:
	@echo "Module Name:        $(MODULE_NAME)"
	@echo "FreeSWITCH Prefix:  $(FREESWITCH_PREFIX)"
	@echo "FreeSWITCH Includes: $(FREESWITCH_INCLUDES)"
	@echo "UniMRCP Prefix:     $(UNIMRCP_PREFIX)"
	@echo "UniMRCP Includes:   $(UNIMRCP_INCLUDES)"
	@echo "APR Includes:       $(APR_INCLUDES)"

# Check dependencies
check-deps:
	@echo "Checking dependencies..."
	@echo -n "FreeSWITCH headers: "
	@test -f $(FREESWITCH_INCLUDES)/switch.h && echo "OK" || echo "NOT FOUND"
	@echo -n "UniMRCP headers: "
	@test -f $(UNIMRCP_INCLUDES)/mrcp_client.h && echo "OK" || echo "NOT FOUND"
	@echo -n "APR headers: "
	@test -f $(APR_INCLUDES)/apr.h && echo "OK" || echo "NOT FOUND"

# Help target
help:
	@echo "mod_unimrcp Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  all         - Build the module (default)"
	@echo "  install     - Install module and configuration files"
	@echo "  uninstall   - Remove installed files"
	@echo "  clean       - Remove build artifacts"
	@echo "  distclean   - Remove all generated files"
	@echo "  config      - Show configuration"
	@echo "  check-deps  - Check for required dependencies"
	@echo "  help        - Show this help"
	@echo ""
	@echo "Variables (can be overridden):"
	@echo "  FREESWITCH_PREFIX  - FreeSWITCH installation prefix"
	@echo "  UNIMRCP_PREFIX     - UniMRCP installation prefix"
	@echo "  APR_INCLUDES       - APR include directory"
	@echo ""
	@echo "Example:"
	@echo "  make FREESWITCH_PREFIX=/opt/freeswitch UNIMRCP_PREFIX=/opt/unimrcp"

.PHONY: all install uninstall clean distclean config check-deps help
