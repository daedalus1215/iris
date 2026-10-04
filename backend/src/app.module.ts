import { Module } from '@nestjs/common';
import { AppleTvModule } from './apple-tv/apple-tv.module';

@Module({
  imports: [AppleTvModule],
  controllers: [],
  providers: [],
})
export class AppModule {}
