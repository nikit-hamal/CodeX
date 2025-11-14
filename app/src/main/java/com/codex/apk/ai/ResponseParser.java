package com.codex.apk.ai;

import com.codex.apk.QwenResponseParser;

public interface ResponseParser {
    QwenResponseParser.ParsedResponse parse(String rawResponse);
}
