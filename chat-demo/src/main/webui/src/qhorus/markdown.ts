// chat-demo/src/main/webui/src/qhorus/markdown.ts
import { marked } from 'marked';
import DOMPurify from 'dompurify';

marked.setOptions({
  breaks: true,
  gfm: true,
});

export function renderMarkdown(content: string): string {
  const raw = marked.parse(content, { async: false }) as string;
  return DOMPurify.sanitize(raw, {
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'em', 'code', 'pre', 'a', 'ul', 'ol', 'li',
      'blockquote', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr', 'del',
      'table', 'thead', 'tbody', 'tr', 'th', 'td', 'img', 'span',
    ],
    ALLOWED_ATTR: ['href', 'title', 'alt', 'src', 'class', 'target'],
  });
}
