package dev.codex.oborokage;
/** Shared animation / hit-window timeline, in server ticks. */
public record Move(int id,int length,int[] hits,int[] hands,float damage,float reach,float arc) {
 public static final int MAIN=1,OFF=2,CROSS=3,COUNTER=4,DODGE=5,ULTIMATE=6,GUARD=7;
 public static Move of(int id){return switch(id){
  case MAIN -> new Move(id,10,new int[]{3},new int[]{0},18,3.8f,105);
  case OFF -> new Move(id,9,new int[]{2},new int[]{1},14,3.1f,115);
  case CROSS -> new Move(id,16,new int[]{4,7},new int[]{0,1},26,4.2f,130);
  case COUNTER -> new Move(id,15,new int[]{3,5},new int[]{1,0},42,4.0f,130);
  case DODGE -> new Move(id,6,new int[]{},new int[]{},0,0,0);
  case ULTIMATE -> new Move(id,48,new int[]{9,13,23},new int[]{0,1,0},65,4.8f,160);
  case GUARD -> new Move(id,600,new int[]{},new int[]{},0,0,0);
  default -> throw new IllegalArgumentException("Unknown move "+id);
 };}
}
