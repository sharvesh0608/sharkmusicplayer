package com.shapps.sharkmusicplayer.other

open class Event<out T>(private val data: T){

  var hasBeenHandled =false
    private set

    fun getContentIfNotHandeled(): T?{
        return  if(hasBeenHandled){
            null
        }else{
            hasBeenHandled=true
            data
        }
    }

    fun peekContent()=data
}