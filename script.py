import re

with open("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/ParticipantsViewBuilder.java", "r") as f:
    content = f.read()

content = re.sub(r'  private static String escapeJsSingleQuoted\(String value\) \{[\s\S]*', '', content)

new_str = r"""  private static String escapeJsSingleQuoted(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }
}"""

content += new_str

with open("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/ParticipantsViewBuilder.java", "w") as f:
    f.write(content)
