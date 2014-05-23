package com.iplanet.services.comm.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Test_PLLRequestServlet {
	@Test
	public void preferredNamingURL(){
		assertEquals("xxx",PLLRequestServlet.fix("xxx", null));
		assertEquals("ACMDM.*\" preferredNamingURL=\"\"><GetNamingProfile>",PLLRequestServlet.fix("ACMDM.*\" preferredNamingURL=\"Z^AsplayName|passport_displayname\">  \" <GetNamingProfile>", null));
		assertEquals("ACMDM.*\" preferredNamingURL=\"https://am2.psb.inside.ru/auth\"><GetNamingProfile>",PLLRequestServlet.fix("ACMDM.*\" preferredNamingURL=\"https://am2.psb.inside.ru/auth\"https://bank.ru:443\" and \"https://bank \r\n<GetNamingProfile>", null));
		assertEquals("ACMDM.*\" preferredNamingURL=\"https://am2.psb.inside.ru/auth\"><GetNamingProfile>",PLLRequestServlet.fix("ACMDM.*\" preferredNamingURL=\"https://am2.psb.inside.ru/auth\">\r\n<GetNamingProfile>", null));
		assertEquals("ACMDM.*\" preferredNamingURL=\"\"><GetNamingProfile>",PLLRequestServlet.fix("ACMDM.*\" preferredNamingURL=\"httpsd:x/\"> \r\n<GetNamingProfile>", null));
	}
}
