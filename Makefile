identifier="com.maxl.java.aips2sqlite"

aips2sqlite:
	./gradlew jar $(ARGS)
.PHONEY: aips2sqlite

clean:
	./gradlew clean
.PHONEY: clean
