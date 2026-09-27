package com.github.catvod.spider;

import com.google.gson.JsonObject;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashMap;

import static com.github.catvod.spider.TestSupport.first;
import static com.github.catvod.spider.TestSupport.firstPlayUrl;
import static com.github.catvod.spider.TestSupport.nonEmptyArray;
import static com.github.catvod.spider.TestSupport.object;
import static com.github.catvod.spider.TestSupport.string;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class OpenListTest {

    private static final String EXT = "[{\"name\":\"139cloud-f\",\"server\":\"https://alist.orzdns.dpdns.org/\",\"startPage\":\"/\",\"realStartPage\":\"/139cloud-f\",\"search\":true,\"username\":\"tv\",\"password\":\"justfortest@@@123\"}]";
    private static final OpenList spider = new OpenList();
    private static JsonObject category;
    private static JsonObject detail;

    @BeforeClass
    public static void setUp() {
        spider.init(null, EXT);
    }

    @Test
    public void homeContent() throws Exception {
        JsonObject result = object(spider.homeContent(false));
        nonEmptyArray(result, "class");
    }

    @Test
    public void categoryContent() {
        nonEmptyArray(category(), "list");
    }

    @Test
    public void detailContent() {
        JsonObject vod = first(detail(), "list");
        assertTrue(string(vod, "vod_play_url").contains("$"));
    }

    @Test
    public void playerContent() {
        String url = firstPlayUrl(detail());
        JsonObject result = object(spider.playerContent("139cloud-f", url, Collections.emptyList()));
        assertTrue(string(result, "url").startsWith("http"));
    }

    private static synchronized JsonObject category() {
        if (category == null) {
            JsonObject home = object(spider.homeContent(false));
            String tid = string(first(home, "class"), "type_id");
            category = object(spider.categoryContent(tid, "1", false, new HashMap<>()));
        }
        return category;
    }

    private static synchronized JsonObject detail() {
        if (detail == null) {
            String id = string(first(category(), "list"), "vod_id");
            detail = object(spider.detailContent(Collections.singletonList(id)));
        }
        return detail;
    }
}
