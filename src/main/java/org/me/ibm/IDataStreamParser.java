package org.me.ibm;

import java.io.IOException;

public interface IDataStreamParser {

	void parse(int firstByte) throws IOException;
	void stop();
}
