import test from 'node:test';
import assert from 'node:assert/strict';
import { joystickDirections } from '../src/controller/joystick.ts';
import { normalizePreferences, defaults } from '../src/controller/preferences.ts';

test('joystick releases all directions inside dead zone', () => {
  assert.deepEqual(joystickDirections(0,0), []);
  assert.deepEqual(joystickDirections(.1,.1), []);
  assert.deepEqual(joystickDirections(NaN,0), []);
});
test('joystick supports diagonal movement and reversals', () => {
  assert.deepEqual(joystickDirections(.7,-.7), ['forward','right']);
  assert.deepEqual(joystickDirections(-.7,.7), ['back','left']);
  assert.deepEqual(joystickDirections(0,-1), ['forward']);
});
test('invalid preferences cannot break controls', () => {
  assert.deepEqual(normalizePreferences(null), defaults);
  assert.deepEqual(normalizePreferences({layout:'invalid', sensitivity:Infinity, buttonSize:1000}), defaults);
});
test('both controller layouts keep valid sensitivity and scale', () => {
  assert.deepEqual(normalizePreferences({layout:'classic', sensitivity:.5, buttonSize:140}), {layout:'classic', sensitivity:.5, buttonSize:140});
  assert.deepEqual(normalizePreferences({layout:'mobile', sensitivity:.03, buttonSize:80}), {layout:'mobile', sensitivity:.03, buttonSize:80});
});
