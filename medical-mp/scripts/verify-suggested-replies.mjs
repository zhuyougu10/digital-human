import assert from 'assert';
import {
  getActiveSuggestionOwnerIndex,
  getRenderableSuggestedReplies,
  processSuggestionTap
} from '../src/utils/suggestion-helper.mjs';

function runScenario(scenario) {
  if (scenario === 'active-owner' || scenario === 'all') {
    assert.strictEqual(getActiveSuggestionOwnerIndex([
      { role: 'assistant', type: 'text', metadata: { suggestedReplies: ['A', 'B'] } }
    ]), 0, 'Valid owner should be selected');
    
    assert.strictEqual(getActiveSuggestionOwnerIndex([
      { role: 'assistant', type: 'text', metadata: { suggestedReplies: ['old'] } },
      { role: 'user', type: 'text' },
      { role: 'assistant', type: 'text', metadata: { suggestedReplies: ['A', 'B'] } }
    ]), 2, 'Valid owner at end of list should be selected');
    
    assert.strictEqual(getActiveSuggestionOwnerIndex([
      { role: 'assistant', type: 'text', metadata: { suggestedReplies: ['A'] } },
      { role: 'user', type: 'text' }
    ]), -1, 'User message at end overrides owner');
  }

  if (scenario === 'no-suggestions' || scenario === 'all') {
    assert.strictEqual(getActiveSuggestionOwnerIndex([
      { role: 'user', type: 'text' },
      { role: 'assistant', type: 'text', metadata: {} }
    ]), -1, 'No suggestions in metadata');
  }

  if (scenario === 'invalid-payload' || scenario === 'all') {
    assert.strictEqual(getActiveSuggestionOwnerIndex(null), -1);
    assert.strictEqual(getActiveSuggestionOwnerIndex([]), -1);
    assert.strictEqual(getActiveSuggestionOwnerIndex([
      { role: 'assistant', type: 'text', metadata: { suggestedReplies: null } }
    ]), -1);
    assert.strictEqual(getActiveSuggestionOwnerIndex([
      { role: 'assistant', type: 'text', metadata: { suggestedReplies: [] } }
    ]), -1);
    assert.strictEqual(getActiveSuggestionOwnerIndex([
      { role: 'assistant', type: 'text', metadata: { suggestedReplies: "string" } }
    ]), -1);
    assert.strictEqual(getActiveSuggestionOwnerIndex([
      { role: 'assistant', type: 'image', metadata: { suggestedReplies: ['A'] } }
    ]), -1);

    assert.deepStrictEqual(
      getRenderableSuggestedReplies(
        { role: 'assistant', type: 'text', metadata: { suggestedReplies: 'string' } },
        true
      ),
      []
    );
    assert.deepStrictEqual(
      getRenderableSuggestedReplies(
        { role: 'assistant', type: 'text', metadata: { suggestedReplies: ['A'] } },
        false
      ),
      []
    );
  }

  if (scenario === 'render-active-only' || scenario === 'all') {
    assert.deepStrictEqual(
      getRenderableSuggestedReplies(
        { role: 'assistant', type: 'text', metadata: { suggestedReplies: ['A', 'B'] } },
        true
      ),
      ['A', 'B']
    );
    assert.deepStrictEqual(
      getRenderableSuggestedReplies(
        { role: 'user', type: 'text', metadata: { suggestedReplies: ['A'] } },
        true
      ),
      []
    );
  }

  if (scenario === 'direct-send' || scenario === 'all') {
    const stateNormal = {
      isSending: false,
      activeIndex: 0,
      messages: [{ role: 'assistant', type: 'text', metadata: { suggestedReplies: ['A'] } }],
      inputText: ''
    };
    
    // Tap with wrong index
    const resultWrongIndex = processSuggestionTap(stateNormal, 'A', 1);
    assert.strictEqual(resultWrongIndex.handled, false, 'Should reject if index does not match activeIndex');

    // Tap with wrong text not in replies
    const resultWrongText = processSuggestionTap(stateNormal, 'B', 0);
    assert.strictEqual(resultWrongText.handled, false, 'Should reject if text is not in suggestedReplies');

    // Valid tap
    const resultNormal = processSuggestionTap(stateNormal, 'A', 0);
    assert.strictEqual(resultNormal.handled, true, 'Valid tap should be handled');
    assert.strictEqual(resultNormal.inputText, 'A', 'Input text should be updated to tap text');
    assert.deepStrictEqual(resultNormal.messages[0].metadata.suggestedReplies, [], 'Stale chips should be cleared');
    assert.deepStrictEqual(stateNormal.messages[0].metadata.suggestedReplies, ['A'], 'State should be immutable');
  }

  if (scenario === 'sending-guard' || scenario === 'all') {
    const stateSending = {
      isSending: true,
      activeIndex: 0,
      messages: [{ role: 'assistant', type: 'text', metadata: { suggestedReplies: ['A'] } }],
      inputText: ''
    };
    const resultSending = processSuggestionTap(stateSending, 'A', 0);
    assert.strictEqual(resultSending.handled, false, 'Should be blocked by isSending');
  }
}

function runTests() {
  const args = process.argv.slice(2);
  const scenarios = args.length > 0 ? args : ['all'];
  
  console.log(`Running tests for scenarios: ${scenarios.join(', ')}...`);
  
  for (const scenario of scenarios) {
    runScenario(scenario);
  }

  console.log('All tests passed!');
}

runTests();
