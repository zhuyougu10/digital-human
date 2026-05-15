export function getActiveSuggestionOwnerIndex(messages) {
  if (!messages || messages.length === 0) return -1;
  const lastIndex = messages.length - 1;
  const lastMsg = messages[lastIndex];
  if (lastMsg.role === 'assistant' && lastMsg.type === 'text' && Array.isArray(lastMsg?.metadata?.suggestedReplies) && lastMsg.metadata.suggestedReplies.length > 0) {
    return lastIndex;
  }
  return -1;
}

export function getRenderableSuggestedReplies(message, isActiveSuggestionOwner) {
  if (!isActiveSuggestionOwner) return [];
  if (!message || message.role !== 'assistant' || message.type !== 'text') return [];
  const replies = message?.metadata?.suggestedReplies;
  return Array.isArray(replies) ? replies : [];
}

export function processSuggestionTap(state, text, tapIndex) {
  if (state.isSending) {
    return { handled: false, messages: state.messages, inputText: state.inputText };
  }

  const activeIndex = state.activeIndex;
  
  if (tapIndex !== activeIndex || activeIndex < 0 || activeIndex >= state.messages.length) {
    return { handled: false, messages: state.messages, inputText: state.inputText };
  }

  const msg = state.messages[activeIndex];
  if (!msg || !msg.metadata || !Array.isArray(msg.metadata.suggestedReplies) || !msg.metadata.suggestedReplies.includes(text)) {
    return { handled: false, messages: state.messages, inputText: state.inputText };
  }

  // Shallow copy the array
  const newMessages = [...state.messages];
  
  // Shallow copy the message
  const newMsg = { ...msg };
  // Shallow copy the metadata and clear suggestedReplies
  newMsg.metadata = { ...newMsg.metadata, suggestedReplies: [] };
  
  newMessages[activeIndex] = newMsg;

  return {
    handled: true,
    messages: newMessages,
    inputText: text
  };
}
