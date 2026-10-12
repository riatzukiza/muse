// SPDX-License-Identifier: GPL-3.0-or-later
// Called by the existing cold compiled-tool gate. These are fixture effects,
// not a model assessment, native review, disk authentication or reservation.
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import fs from 'node:fs';
import {syncBuiltinESMExports} from 'node:module';
import path from 'node:path';

const sha = value => createHash('sha256').update(value).digest('hex');
const protocol = 'open-hax.review-context/v1';
const stages = ['deterministic', 'map-change', 'generate-candidates', 'adversarial-validate', 'publish'];

export async function verifyReviewContext({fixture, call, stageInput}) {
  const make = (name, context) => {
    const evidence = stageInput(`context-${name}`);
    const file = path.join(evidence, 'pr-context.md');
    if (context !== null) fs.writeFileSync(file, context);
    return {evidence, file, ctx: {sessionID: name, directory: path.dirname(path.dirname(evidence))}};
  };
  const bytes = Buffer.from(('Untrusted PR data: ημ 😀\n').repeat(2200) + 'EXACT-TAIL: 終わり\n');
  const large = make('1482-pages', bytes);
  // Four header lines plus 189692 changed lines gives the observed 1482 pages.
  const diff = Buffer.from('diff --git a/large b/large\n--- a/large\n+++ b/large\n@@ -0,0 +1,189692 @@\n' +
    '+hunk\n'.repeat(189692));
  const manifestPath = path.join(large.evidence, 'input-manifest.json');
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  manifest.full_diff = {path: 'basehead.diff', bytes: diff.length, sha256: sha(diff)};
  fs.writeFileSync(path.join(large.evidence, 'basehead.diff'), diff);
  fs.writeFileSync(manifestPath, JSON.stringify(manifest));
  const begin = await call('review_begin', {}, large.ctx);
  assert.equal(begin['ok?'], true);
  assert.equal(begin['input-coverage'].chunks, 1482);
  assert.equal(begin['context-protocol'], protocol);
  const source = {path: large.file, bytes: bytes.length, sha256: sha(bytes)};
  assert.deepEqual(begin['context-source'], source);
  assert.equal(Object.hasOwn(begin, 'pr-context'), false, 'never inline the large context');
  assert.ok(Buffer.byteLength(JSON.stringify(begin)) < 49152, 'begin remains below the clipping bound');
  assert.ok(Buffer.byteLength(JSON.stringify({...begin, 'pr-context': bytes.toString('utf8')})) > 49152,
    'retain the actual old inline-response clipping negative');
  const recovered = [];
  const handle = fs.openSync(source.path, 'r');
  try {
    for (let offset = 0; offset < source.bytes; offset += 4096) {
      const page = Buffer.alloc(Math.min(4096, source.bytes - offset));
      assert.equal(fs.readSync(handle, page, 0, page.length, offset), page.length);
      recovered.push(page);
    }
  } finally {fs.closeSync(handle);}
  assert.deepEqual(Buffer.concat(recovered), bytes, 'bounded reads preserve multiline Unicode and the tail');
  assert.equal(sha(Buffer.concat(recovered)), source.sha256);

  const assess = async f => {
    const begun = await call('review_begin', {}, f.ctx);
    assert.equal(begun['ok?'], true);
    for (let id = 1; id <= begun['input-coverage'].chunks; id++) {
      assert.equal((await call('review_read_diff_chunk', {id}, f.ctx))['ok?'], true);
      assert.equal((await call('review_assess_diff_chunk', {id, note: 'Synthetic complete input assessment'}, f.ctx))['ok?'], true);
    }
  };
  const advance = async f => {
    for (const stage of stages) assert.equal((await call('review_record_evidence',
      {stage, note: 'Synthetic stage after context custody check'}, f.ctx))['ok?'], true);
  };
  for (const operation of ['review_record_evidence', 'review_submit']) {
    for (const change of ['changed', 'truncated', 'disappeared', 'appeared', 'EACCES', 'EIO', 'ENOENT-race']) {
      const f = make(`${operation}-${change}`, change === 'appeared' ? null : bytes);
      await assess(f);
      if (operation === 'review_submit') await advance(f);
      if (change === 'changed') fs.writeFileSync(f.file, Buffer.from(bytes).fill(120, 0, 1));
      if (change === 'truncated') fs.writeFileSync(f.file, bytes.subarray(0, bytes.length - 1));
      if (change === 'disappeared') fs.unlinkSync(f.file);
      if (change === 'appeared') fs.writeFileSync(f.file, bytes);
      const read = fs.readFileSync;
      if (['EACCES', 'EIO', 'ENOENT-race'].includes(change)) {
        fs.readFileSync = (filename, ...args) => {
          if (filename === f.file) throw Object.assign(new Error('DO-NOT-LEAK synthetic secret'),
            {code: change === 'ENOENT-race' ? 'ENOENT' : change});
          return read(filename, ...args);
        };
        syncBuiltinESMExports();
      }
      let result;
      try {result = await call(operation, operation === 'review_submit' ? {summary: 'Must refuse'} :
        {stage: 'deterministic', note: 'Must refuse'}, f.ctx);}
      finally {fs.readFileSync = read; syncBuiltinESMExports();}
      assert.equal(result['ok?'], false, `${operation} refuses ${change}`);
      assert.equal(result['context-protocol'], protocol);
      assert.equal(result.code, ['EACCES', 'EIO', 'ENOENT-race'].includes(change) ? 'unavailable' : 'changed');
      if (['EACCES', 'EIO', 'ENOENT-race'].includes(change)) {
        assert.deepEqual(result['context-observation'], {status: 'unavailable',
          'error-code': change === 'ENOENT-race' ? 'ENOENT' : change});
      }
      assert.equal(JSON.stringify(result).includes('DO-NOT-LEAK'), false);
      const status = await call('review_status', {}, f.ctx);
      assert.equal(status['restart-required?'], true, 'custody refusal uses the established restart indicator');
      assert.equal(result['restart-required?'], true, 'refused effect reports required restart');
      assert.equal(status.stage, operation === 'review_submit' ? 'publish' : 'deterministic');
      assert.equal(status['evidence-count'], operation === 'review_submit' ? 5 : 0);
      assert.equal(fs.existsSync(path.join(f.evidence, 'submission.json')), false);
      if (change === 'appeared') fs.unlinkSync(f.file);
      else fs.writeFileSync(f.file, bytes);
      assert.equal((await call('review_submit', {summary: 'Restored observation cannot repair a failed invocation'}, f.ctx))['ok?'], false);
    }
  }
  for (const code of ['EACCES', 'EIO']) {
    const f = make(`first-${code}`, bytes), read = fs.readFileSync;
    const sentinel = path.join(f.evidence, 'submission.json');
    fs.writeFileSync(sentinel, 'owned pre-existing sentinel');
    fs.readFileSync = (filename, ...args) => {
      if (filename === f.file) throw Object.assign(new Error('DO-NOT-LEAK'), {code});
      return read(filename, ...args);
    };
    syncBuiltinESMExports();
    let result;
    try {result = await call('review_begin', {}, f.ctx);}
    finally {fs.readFileSync = read; syncBuiltinESMExports();}
    assert.equal(result['ok?'], false);
    assert.equal(result.code, 'unavailable');
    assert.deepEqual(result['context-observation'], {status: 'unavailable', 'error-code': code});
    assert.equal(JSON.stringify(result).includes('DO-NOT-LEAK'), false);
    assert.equal((await call('review_status', {}, f.ctx))['ok?'], false, 'no admitted session on unavailable begin');
    assert.equal(fs.readFileSync(sentinel, 'utf8'), 'owned pre-existing sentinel', 'unavailable begin performs no artifact cleanup');
    // The unavailable begin never admitted or mutated a session.
    assert.equal((await call('review_begin', {}, f.ctx))['ok?'], true);
    assert.equal(fs.existsSync(sentinel), false, 'a later successful admission retains normal cleanup');
  }
  for (const context of [null, bytes]) {
    const f = make(context === null ? 'absent-positive' : 'present-positive', context);
    await assess(f); await advance(f);
    assert.equal((await call('review_status', {}, f.ctx))['restart-required?'], false,
      'healthy present and absent context do not require restart');
    const result = await call('review_submit', {summary: 'Fixture custody verified; no model assessment'}, f.ctx);
    assert.equal(result['ok?'], true);
    assert.deepEqual(result['context-source'], context === null ? null :
      {path: f.file, bytes: bytes.length, sha256: sha(bytes)});
  }
  assert.ok(fs.existsSync(fixture));
  return {context_protocol: protocol, observed_pages: 1482, bounded_begin_bytes: Buffer.byteLength(JSON.stringify(begin)),
    context_bytes: bytes.length, context_sha256: sha(bytes), custody_negatives: 14,
    first_unavailable_negatives: 2, native_review: false};
}
