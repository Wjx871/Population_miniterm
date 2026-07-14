import { ref, watch, onMounted } from 'vue'

export function useNumberAnimation(targetValueRef, duration = 1500) {
  const displayValue = ref(0)
  
  let startTime = null
  let startValue = 0
  let animationFrame = null
  
  const animate = (timestamp) => {
    if (!startTime) startTime = timestamp
    const progress = Math.min((timestamp - startTime) / duration, 1)
    
    // Ease out quart
    const easeOut = 1 - Math.pow(1 - progress, 4)
    const target = Number(targetValueRef.value) || 0
    
    displayValue.value = Math.floor(startValue + (target - startValue) * easeOut)
    
    if (progress < 1) {
      animationFrame = requestAnimationFrame(animate)
    } else {
      displayValue.value = target
    }
  }
  
  const startAnimation = () => {
    if (animationFrame) cancelAnimationFrame(animationFrame)
    startValue = displayValue.value
    startTime = null
    animationFrame = requestAnimationFrame(animate)
  }
  
  watch(targetValueRef, () => {
    startAnimation()
  })
  
  onMounted(() => {
    startAnimation()
  })
  
  return displayValue
}
