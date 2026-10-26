// Actual built Muse server probe. This is direct MCP evidence, not a Codex session.
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtempSync, readFileSync, readdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';

const artifact=resolve('.codex/dist/receipt-river.js');
const directory=mkdtempSync(join(tmpdir(),'muse-hac01-probe-'));
const before=readdirSync(directory);
const client=new Client({name:'hac01-direct-muse-probe',version:'1.0.0'});
try {
  await client.connect(new StdioClientTransport({command:'node',args:[artifact],cwd:directory,stderr:'pipe'}));
  const {tools}=await client.listTools();
  const receipt=tools.find(tool=>tool.name==='receipt_status');
  assert.ok(receipt,'Compiled shared receipt tool is registered');
  assert.deepEqual(tools.map(tool=>tool.name),['receipt_status']);
  const result=await client.callTool({name:'receipt_status',arguments:{}});
  assert.notEqual(result.isError,true);
  const body=JSON.parse(result.content.find(item=>item.type==='text').text);
  assert.equal(body.action,'status');
  assert.equal(body.exists,false);
  assert.equal(body.count,0);
  assert.deepEqual(readdirSync(directory),before,'Standalone startup and read-only status create no files');
  const denied=await client.callTool({name:'actor_spawn',arguments:{}});
  assert.equal(denied.isError,true,'Actor tools remain unavailable');
  assert.deepEqual(readdirSync(directory),before);
  console.log(JSON.stringify({status:'pass',artifactSha256:createHash('sha256').update(readFileSync(artifact)).digest('hex'),toolCount:tools.length,tool:receipt.name,result:{action:body.action,exists:body.exists,count:body.count},unchanged:true,scope:'direct MCP; not fresh Codex session or full profile qualification'}));
} finally {await client.close();}
