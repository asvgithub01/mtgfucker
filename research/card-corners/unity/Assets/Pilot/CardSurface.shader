Shader "Pilot/CardSurface" {
 Properties { _MainTex("Texture",2D)="white"{} _Tint("Tint",Color)=(1,1,1,1) _Foil("Foil approximation",Float)=0 _Gloss("Gloss",Float)=0 _Sleeve("Sleeve",Float)=0 }
 SubShader { Tags { "Queue"="Transparent" } Cull Back Blend SrcAlpha OneMinusSrcAlpha
 Pass { CGPROGRAM
 #pragma vertex vert
 #pragma fragment frag
 #include "UnityCG.cginc"
 sampler2D _MainTex; float4 _Tint; float _Foil,_Gloss,_Sleeve;
 float4 _PilotLight; float _PilotIntensity;
 struct V { float4 vertex:POSITION; float2 uv:TEXCOORD0; float3 normal:NORMAL; };
 struct F { float4 pos:SV_POSITION; float2 uv:TEXCOORD0; float3 normal:TEXCOORD1; float3 world:TEXCOORD2; };
 F vert(V v) { F o; o.pos=UnityObjectToClipPos(v.vertex); o.uv=v.uv; o.normal=UnityObjectToWorldNormal(v.normal); o.world=mul(unity_ObjectToWorld,v.vertex).xyz; return o; }
 fixed4 frag(F i):SV_Target {
  float3 n=normalize(i.normal), l=normalize(_PilotLight.xyz-i.world), view=normalize(_WorldSpaceCameraPos-i.world);
  float diffuse=.25+_PilotIntensity*max(0,dot(n,l));
  float spec=pow(max(0,dot(n,normalize(l+view))),32)*_Gloss;
  float3 rainbow=.5+.5*cos(6.28318*(i.uv.x*3+i.uv.y+dot(n,view)+float3(0,.333,.667)));
  float3 c=tex2D(_MainTex,i.uv).rgb*_Tint.rgb*diffuse+spec+_Foil*rainbow*spec;
  return fixed4(c, _Sleeve>0 ? saturate(.035+spec*.35) : 1);
 }
 ENDCG } }
}
