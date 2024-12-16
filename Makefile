#  var
JAVAC = javac -cp poisson/src/
BASE_DIR = ds/assign

# Targets
.PHONY: all trg tom p2p clean

all: trg tom p2p

trg:
	$(JAVAC) $(BASE_DIR)/trg/Peer.java
	$(JAVAC) $(BASE_DIR)/trg/server/Server.java
	$(JAVAC) $(BASE_DIR)/trg/Injector.java
	@echo ""

tom:
	$(JAVAC) $(BASE_DIR)/tom/Peer.java
	@echo ""

p2p:
	$(JAVAC) $(BASE_DIR)/p2p/Peer.java
	@echo ""

clean:
	find ds/ -name "*.class" -delete
	@echo "Clean completed."
