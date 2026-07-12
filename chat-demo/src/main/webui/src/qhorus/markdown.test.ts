// chat-demo/src/main/webui/src/qhorus/markdown.test.ts
import { describe, it, expect } from 'vitest';
import { renderMarkdown } from './markdown.js';

describe('renderMarkdown', () => {
  it('renders plain text as paragraph', () => {
    expect(renderMarkdown('hello world')).toContain('hello world');
  });

  it('renders bold and italic', () => {
    const html = renderMarkdown('**bold** and *italic*');
    expect(html).toContain('<strong>bold</strong>');
    expect(html).toContain('<em>italic</em>');
  });

  it('renders code blocks', () => {
    const html = renderMarkdown('```\nconst x = 1;\n```');
    expect(html).toContain('<code>');
  });

  it('renders inline code', () => {
    expect(renderMarkdown('use `foo()`')).toContain('<code>foo()</code>');
  });

  it('sanitises script tags', () => {
    const html = renderMarkdown('<script>alert("xss")</script>');
    expect(html).not.toContain('<script>');
  });

  it('sanitises event handlers', () => {
    const html = renderMarkdown('<img onerror="alert(1)" src="x">');
    expect(html).not.toContain('onerror');
  });

  it('allows links with href', () => {
    const html = renderMarkdown('[click](https://example.com)');
    expect(html).toContain('href="https://example.com"');
  });

  it('strips javascript: URIs', () => {
    const html = renderMarkdown('[xss](javascript:alert(1))');
    expect(html).not.toContain('javascript:');
  });

  it('strips data:text/html image XSS vectors', () => {
    const html = renderMarkdown('<img src="data:text/html,<script>alert(1)</script>">');
    expect(html).not.toContain('data:text/html');
  });

  it('strips javascript: image XSS vectors', () => {
    const html = renderMarkdown('<img src="javascript:alert(1)">');
    expect(html).not.toContain('javascript:');
  });

  it('converts newlines to <br> with breaks:true', () => {
    const html = renderMarkdown('line1\nline2');
    expect(html).toContain('<br');
  });

  it('renders tables', () => {
    const md = '| A | B |\n|---|---|\n| 1 | 2 |';
    const html = renderMarkdown(md);
    expect(html).toContain('<table');
    expect(html).toContain('<thead');
    expect(html).toContain('<tbody');
    expect(html).toContain('<tr');
    expect(html).toContain('<th');
    expect(html).toContain('<td');
  });

  it('renders headings', () => {
    const html = renderMarkdown('# Heading');
    expect(html).toContain('<h1');
  });

  it('renders blockquotes', () => {
    const html = renderMarkdown('> quoted');
    expect(html).toContain('<blockquote');
  });

  it('renders unordered lists', () => {
    const html = renderMarkdown('- item');
    expect(html).toContain('<ul');
    expect(html).toContain('<li');
  });

  it('renders ordered lists', () => {
    const html = renderMarkdown('1. item');
    expect(html).toContain('<ol');
    expect(html).toContain('<li');
  });

  it('renders fenced code blocks with <pre> and <code>', () => {
    const html = renderMarkdown('```\nconst x = 1;\n```');
    expect(html).toContain('<pre');
    expect(html).toContain('<code');
  });
});
